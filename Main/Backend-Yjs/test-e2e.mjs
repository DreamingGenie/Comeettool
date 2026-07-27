import assert from 'node:assert/strict'
import {spawn} from 'node:child_process'
import crypto from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import * as Y from 'yjs'
import {WebSocket} from 'ws'
import {HocuspocusProvider} from '@hocuspocus/provider'
import pg from 'pg'

const rootDir = path.dirname(fileURLToPath(import.meta.url))
const port = Number(process.env.TEST_PORT || 3210)
const httpUrl = `http://127.0.0.1:${port}`
const webSocketUrl = `ws://127.0.0.1:${port}/collaboration`
const databaseUrl = process.env.TEST_DATABASE_URL || process.env.DATABASE_URL
const serverFile = process.env.TEST_SERVER_FILE || 'CrdtServer.js'
const timeout = 10_000
const {Pool} = pg
const pool = new Pool({connectionString: databaseUrl, max: 2})
const providers = new Set()

let serverProcess
let serverOutput = ''
let createdDocument
let teamId
let createdTestTeam = false

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))

async function waitFor(predicate, description, waitTimeout = timeout) {
    const deadline = Date.now() + waitTimeout
    let lastError

    while (Date.now() < deadline) {
        try {
            if (await predicate()) return
        } catch (error) {
            lastError = error
        }

        await delay(50)
    }

    throw new Error(
        `${description} 시간 초과${lastError ? `: ${lastError.message}` : ''}`,
    )
}

async function waitForHealth() {
    await waitFor(async () => {
        const response = await fetch(`${httpUrl}/health`).catch(() => null)
        return response?.status === 200
    }, '서버 Health 확인')
}

async function startServer() {
    serverOutput = ''
    serverProcess = spawn(process.execPath, [serverFile], {
        cwd: rootDir,
        env: {
            ...process.env,
            PORT: String(port),
            DATABASE_URL: databaseUrl,
        },
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true,
    })

    serverProcess.stdout.on('data', chunk => {
        serverOutput += chunk.toString()
    })
    serverProcess.stderr.on('data', chunk => {
        serverOutput += chunk.toString()
    })

    await Promise.race([
        waitForHealth(),
        new Promise((_, reject) => {
            serverProcess.once('exit', code => {
                reject(new Error(
                    `서버가 비정상 종료되었습니다(${code}).\n${serverOutput}`,
                ))
            })
        }),
    ])
}

async function stopServer() {
    if (!serverProcess || serverProcess.exitCode !== null) return

    const exited = new Promise(resolve => serverProcess.once('exit', resolve))
    serverProcess.kill('SIGINT')

    await Promise.race([
        exited,
        delay(3_000).then(() => {
            if (serverProcess?.exitCode === null) serverProcess.kill()
        }),
    ])
    serverProcess = undefined
}

function createProvider(documentName) {
    const document = new Y.Doc()
    let lastStatus = 'initializing'
    const provider = new HocuspocusProvider({
        url: webSocketUrl,
        name: documentName,
        document,
        WebSocketPolyfill: WebSocket,
        onStatus: ({status}) => {
            lastStatus = status
        },
    })

    provider.getLastTestStatus = () => lastStatus
    providers.add(provider)
    return provider
}

function destroyProvider(provider) {
    if (!provider) return
    providers.delete(provider)
    provider.destroy()
    provider.document.destroy()
}

async function waitForSync(provider) {
    await waitFor(
        () => provider.synced,
        `Yjs 동기화(마지막 상태: ${provider.getLastTestStatus()})`,
    )
}

function decodeText(state) {
    const document = new Y.Doc()

    try {
        Y.applyUpdate(document, state)
        return document.getText('e2e-content').toString()
    } finally {
        document.destroy()
    }
}

async function readStoredText(documentId) {
    const result = await pool.query(
        `SELECT yjs_state
           FROM documents_state
          WHERE document_id = $1`,
        [documentId],
    )

    return result.rowCount === 0
        ? null
        : decodeText(result.rows[0].yjs_state)
}

async function cleanup() {
    if (createdDocument) {
        await pool.query(
            `DELETE FROM documents WHERE document_id = $1`,
            [createdDocument.id],
        )
    }

    if (createdTestTeam) {
        await pool.query(
            `DELETE FROM teams WHERE team_id = $1`,
            [teamId],
        )
    }
}

async function check(name, operation) {
    process.stdout.write(`- ${name} ... `)
    await operation()
    console.log('PASS')
}

async function main() {
    console.log(`Backend-Yjs PostgreSQL E2E 테스트 시작 (port: ${port})`)

    await check('1. PostgreSQL 연결과 테스트 팀 확인', async () => {
        const connected = await pool.query('SELECT 1 AS connected')
        assert.equal(connected.rows[0].connected, 1)

        if (process.env.TEST_TEAM_ID) {
            teamId = Number(process.env.TEST_TEAM_ID)
        } else {
            const team = await pool.query(
                `SELECT team_id FROM teams ORDER BY team_id LIMIT 1`,
            )

            if (team.rowCount > 0) {
                teamId = Number(team.rows[0].team_id)
            } else {
                teamId = Date.now()
                await pool.query(
                    `INSERT INTO teams (
                        team_id,
                        team_name,
                        team_owner_id
                     ) VALUES ($1, $2, $3)`,
                    [teamId, `CRDT E2E ${teamId}`, 0],
                )
                createdTestTeam = true
            }
        }
    })

    await startServer()

    await check('2. GET /health가 PostgreSQL 상태와 200을 반환', async () => {
        const response = await fetch(`${httpUrl}/health`)
        assert.equal(response.status, 200)
        assert.deepEqual(await response.json(), {
            status: 'ok',
            server: 'postgres-crdt',
            database: 'postgresql',
        })
    })

    await check('3. 문서와 초기 Yjs 상태를 트랜잭션으로 생성', async () => {
        const response = await fetch(`${httpUrl}/api/documents`, {
            method: 'POST',
            headers: {'content-type': 'application/json'},
            body: JSON.stringify({
                title: `E2E test ${new Date().toISOString()}`,
                teamId,
            }),
        })
        assert.equal(response.status, 201)
        createdDocument = await response.json()

        const result = await pool.query(
            `SELECT d.document_id, s.binary_size, octet_length(s.state_hash) hash_size
               FROM documents d
               JOIN documents_state s ON s.document_id = d.document_id
              WHERE d.document_id = $1`,
            [createdDocument.id],
        )
        assert.equal(result.rowCount, 1)
        assert.ok(result.rows[0].binary_size > 0)
        assert.equal(result.rows[0].hash_size, 32)
    })

    const documentName =
        `document:${createdDocument.id}:epoch:${createdDocument.stateEpoch}`
    const originalText = `공동 편집 ${Date.now()}`
    let firstProvider
    let secondProvider

    await check('4. 두 사용자의 변경 내용을 실시간 동기화', async () => {
        firstProvider = createProvider(documentName)
        secondProvider = createProvider(documentName)
        await Promise.all([
            waitForSync(firstProvider),
            waitForSync(secondProvider),
        ])

        firstProvider.document.getText('e2e-content').insert(0, originalText)
        await waitFor(
            () => secondProvider.document
                .getText('e2e-content')
                .toString() === originalText,
            '두 번째 사용자 변경 수신',
        )
    })

    await check('5. Yjs 상태를 documents_state BYTEA에 저장', async () => {
        await waitFor(
            async () => await readStoredText(createdDocument.id) === originalText,
            'PostgreSQL Yjs 상태 저장',
        )
    })

    await check('6. 서버 재시작과 늦은 입장 시 기존 내용 복구', async () => {
        destroyProvider(firstProvider)
        destroyProvider(secondProvider)
        firstProvider = undefined
        secondProvider = undefined

        await stopServer()
        await startServer()

        const lateProvider = createProvider(documentName)
        await waitForSync(lateProvider)
        assert.equal(
            lateProvider.document.getText('e2e-content').toString(),
            originalText,
        )
        destroyProvider(lateProvider)
    })

    let version

    await check('7. 현재 상태를 documents_version에 저장', async () => {
        const response = await fetch(
            `${httpUrl}/api/documents/${createdDocument.id}/versions`,
            {
                method: 'POST',
                headers: {'content-type': 'application/json'},
                body: JSON.stringify({
                    triggerType: 'manual',
                    editorJson: {type: 'doc', content: []},
                    requestId: crypto.randomUUID(),
                }),
            },
        )
        assert.equal(response.status, 201)
        version = await response.json()
        assert.equal(version.versionNumber, 1)
    })

    await check('8. 저장한 버전으로 문서 상태 복구', async () => {
        const changedProvider = createProvider(documentName)
        await waitForSync(changedProvider)
        changedProvider.document
            .getText('e2e-content')
            .insert(originalText.length, ' 변경됨')
        await waitFor(
            async () => await readStoredText(createdDocument.id)
                === `${originalText} 변경됨`,
            '복구 전 변경 상태 저장',
        )
        destroyProvider(changedProvider)

        const response = await fetch(
            `${httpUrl}/api/documents/${createdDocument.id}`
            + `/versions/${version.id}/restore`,
            {
                method: 'POST',
                headers: {'content-type': 'application/json'},
                body: '{}',
            },
        )
        assert.equal(response.status, 200)

        const restoredProvider = createProvider(documentName)
        await waitForSync(restoredProvider)
        assert.equal(
            restoredProvider.document.getText('e2e-content').toString(),
            originalText,
        )
        destroyProvider(restoredProvider)
    })

    await check('9. 문서를 PostgreSQL에서 소프트 삭제', async () => {
        const response = await fetch(
            `${httpUrl}/api/documents/${createdDocument.id}`,
            {method: 'DELETE'},
        )
        assert.equal(response.status, 204)

        const result = await pool.query(
            `SELECT is_deleted, deleted_at IS NOT NULL AS has_deleted_at
               FROM documents
              WHERE document_id = $1`,
            [createdDocument.id],
        )
        assert.equal(result.rows[0].is_deleted, true)
        assert.equal(result.rows[0].has_deleted_at, true)
    })

    console.log('\n결과: PostgreSQL 기반 테스트 9개를 모두 통과했습니다.')
}

try {
    await main()
} catch (error) {
    console.error('\nFAIL:', error)
    if (serverOutput) console.error('\n서버 출력:\n', serverOutput)
    process.exitCode = 1
} finally {
    for (const provider of [...providers]) destroyProvider(provider)
    await stopServer()
    await cleanup().catch(error => {
        console.error('테스트 문서 정리 실패:', error)
        process.exitCode = 1
    })
    await pool.end()
}

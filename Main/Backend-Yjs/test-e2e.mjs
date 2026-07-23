import assert from 'node:assert/strict'
import {spawn} from 'node:child_process'
import fs from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import * as Y from 'yjs'
import {WebSocket} from 'ws'
import {HocuspocusProvider} from '@hocuspocus/provider'

const rootDir = path.dirname(fileURLToPath(import.meta.url))
const port = Number(process.env.TEST_PORT || 3210)
const httpUrl = `http://127.0.0.1:${port}`
const webSocketUrl = `ws://127.0.0.1:${port}/collaboration`
const timeout = 8_000
const createdProviders = new Set()

let serverProcess
let serverOutput = ''
let createdDocument

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
    serverProcess = spawn(process.execPath, ['CrdtServer.js'], {
        cwd: rootDir,
        env: {...process.env, PORT: String(port)},
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
                reject(new Error(`서버가 비정상 종료되었습니다(${code}).\n${serverOutput}`))
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
            if (serverProcess.exitCode === null) serverProcess.kill()
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
    createdProviders.add(provider)
    return provider
}

function destroyProvider(provider) {
    if (!provider) return
    createdProviders.delete(provider)
    provider.destroy()
    provider.document.destroy()
}

async function waitForSync(provider, waitTimeout = timeout) {
    await waitFor(
        () => {
            if (provider.synced) return true
            throw new Error(`마지막 연결 상태: ${provider.getLastTestStatus()}`)
        },
        'Yjs 클라이언트 동기화',
        waitTimeout,
    )
}

async function readSavedText(stateFile) {
    const ydoc = new Y.Doc()
    try {
        Y.applyUpdate(ydoc, await fs.readFile(stateFile))
        return ydoc.getText('e2e-content').toString()
    } finally {
        ydoc.destroy()
    }
}

async function removeTestDocument() {
    if (!createdDocument) return

    const metadataFile = path.join(rootDir, 'data', 'documents.json')
    const documents = JSON.parse(await fs.readFile(metadataFile, 'utf8'))
    const remainingDocuments = documents.filter(
        item => item.id !== createdDocument.id,
    )
    await fs.writeFile(
        metadataFile,
        `${JSON.stringify(remainingDocuments, null, 2)}\n`,
        'utf8',
    )

    await fs.rm(
        path.join(rootDir, 'data', 'states', `${createdDocument.id}.bin`),
        {force: true},
    )
}

async function check(name, operation) {
    process.stdout.write(`- ${name} ... `)
    await operation()
    console.log('PASS')
}

async function main() {
    console.log(`Backend-Yjs E2E 테스트 시작 (port: ${port})`)
    await startServer()

    await check('1. GET /health가 200 OK를 반환', async () => {
        const response = await fetch(`${httpUrl}/health`)
        assert.equal(response.status, 200)
        assert.deepEqual(
            await response.json(),
            {status: 'ok', server: 'file-crdt'},
        )
    })

    let stateFile
    let documentName

    await check('2. 문서 메타데이터와 초기 .bin 파일 생성', async () => {
        const response = await fetch(`${httpUrl}/api/documents`, {
            method: 'POST',
            headers: {'content-type': 'application/json'},
            body: JSON.stringify({
                title: `E2E test ${new Date().toISOString()}`,
                createdBy: 'e2e-test',
            }),
        })
        assert.equal(response.status, 201)
        createdDocument = await response.json()
        documentName =
            `document:${createdDocument.id}:epoch:${createdDocument.stateEpoch}`
        stateFile = path.join(
            rootDir,
            'data',
            'states',
            `${createdDocument.id}.bin`,
        )

        const documents = JSON.parse(
            await fs.readFile(path.join(rootDir, 'data', 'documents.json'), 'utf8'),
        )
        assert.ok(documents.some(item => item.id === createdDocument.id))
        assert.ok((await fs.stat(stateFile)).size > 0)
    })

    let firstProvider
    let secondProvider
    const expectedText = `공동 편집 ${Date.now()}`

    await check('3. 두 사용자 사이의 변경 내용 실시간 전달', async () => {
        firstProvider = createProvider(documentName)
        secondProvider = createProvider(documentName)
        await Promise.all([
            waitForSync(firstProvider),
            waitForSync(secondProvider),
        ])

        firstProvider.document.getText('e2e-content').insert(0, expectedText)
        await waitFor(
            () => secondProvider.document
                .getText('e2e-content')
                .toString() === expectedText,
            '두 번째 사용자에게 실시간 변경 전달',
        )
    })

    await check('4. 편집 내용이 .bin 상태 파일에 저장', async () => {
        await waitFor(
            async () => await readSavedText(stateFile) === expectedText,
            'Yjs 상태 파일 저장',
        )
    })

    await check('5. 서버 재시작 후 기존 문서 내용 복구', async () => {
        destroyProvider(firstProvider)
        destroyProvider(secondProvider)
        firstProvider = undefined
        secondProvider = undefined

        await stopServer()
        await startServer()

        const restoredProvider = createProvider(documentName)
        await waitForSync(restoredProvider)
        assert.equal(
            restoredProvider.document.getText('e2e-content').toString(),
            expectedText,
        )
        destroyProvider(restoredProvider)
    })

    await check('6. 늦게 접속한 사용자가 기존 내용을 수신', async () => {
        const lateProvider = createProvider(documentName)
        await waitForSync(lateProvider)
        assert.equal(
            lateProvider.document.getText('e2e-content').toString(),
            expectedText,
        )
        destroyProvider(lateProvider)
    })

    await check('7. 잘못된 Epoch 연결 거부', async () => {
        const invalidName =
            `document:${createdDocument.id}:epoch:${createdDocument.stateEpoch + 1}`
        const invalidProvider = createProvider(invalidName)

        await delay(2_000)
        assert.equal(
            invalidProvider.synced,
            false,
            '잘못된 Epoch 클라이언트가 동기화되었습니다.',
        )
        assert.equal(
            invalidProvider.document.getText('e2e-content').toString(),
            '',
            '잘못된 Epoch 클라이언트가 기존 내용을 받았습니다.',
        )
        destroyProvider(invalidProvider)
    })

    await check('8. 잘못된 Epoch 변경이 정상 문서와 상태 파일에 반영되지 않음', async () => {
        const invalidName =
            `document:${createdDocument.id}:epoch:${createdDocument.stateEpoch + 1}`
        const invalidProvider = createProvider(invalidName)
        invalidProvider.document
            .getText('e2e-content')
            .insert(0, '반영되면 안 되는 내용')
        await delay(2_000)

        assert.equal(await readSavedText(stateFile), expectedText)
        destroyProvider(invalidProvider)
    })

    console.log('\n결과: 8개 테스트를 모두 통과했습니다.')
}

try {
    await main()
} catch (error) {
    console.error('\nFAIL:', error)
    if (serverOutput) console.error('\n서버 출력:\n', serverOutput)
    process.exitCode = 1
} finally {
    for (const provider of [...createdProviders]) destroyProvider(provider)
    await stopServer()
    await removeTestDocument().catch(error => {
        console.error('테스트 문서 정리 실패:', error)
        process.exitCode = 1
    })
}
import express from 'express'
import http from 'node:http'
import crypto from 'node:crypto'
import * as Y from 'yjs'
import {Hocuspocus} from '@hocuspocus/server'
import {Database} from '@hocuspocus/extension-database'
import {WebSocketServer} from 'ws'
import pg from 'pg'
import {
    createCollaborationAuthenticator,
    parseDocumentName,
} from './collaboration-auth.js'

const {Pool} = pg
const port = Number(process.env.PORT || 3000)
const databaseUrl = process.env.DATABASE_URL
const springJwksUrl = process.env.SPRING_JWKS_URL
    || 'http://localhost:8080/.well-known/jwks.json'
const jwtIssuer = process.env.JWT_ISSUER || 'a707-api'
const jwtYjsAudience = process.env.JWT_YJS_AUDIENCE || 'a707-yjs'
const internalApiToken = process.env.YJS_INTERNAL_TOKEN
    || 'local-yjs-internal-token'

if (!databaseUrl) {
    throw new Error('DATABASE_URL 환경변수가 필요합니다.')
}

const pool = new Pool({
    connectionString: databaseUrl,
    max: 10,
})

function createEmptyYjsState() {
    const document = new Y.Doc()

    try {
        return Buffer.from(Y.encodeStateAsUpdate(document))
    } finally {
        document.destroy()
    }
}

function hashState(state) {
    return crypto.createHash('sha256').update(state).digest()
}

function mapDocument(row) {
    return {
        id: row.document_id,
        teamId: Number(row.team_id),
        title: row.document_title,
        finalVersion: row.final_version,
        currentRevision: Number(row.persisted_revision ?? 0),
        stateEpoch: 1,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
    }
}

function mapVersion(row) {
    return {
        id: row.document_version_id,
        documentId: row.document_id,
        versionNumber: row.version_number,
        title: row.title_snapshot,
        editorJson: row.editor_json,
        triggerType: row.trigger_type,
        binarySize: row.binary_size,
        schemaVersion: row.schema_version,
        sourceVersionId: row.source_version_id,
        requestId: row.request_id,
        createdAt: row.created_at,
    }
}

async function getActiveDocument(documentId, client = pool) {
    const result = await client.query(
        `SELECT d.*, COALESCE(s.persisted_revision, 0) AS persisted_revision
         FROM documents d
                  LEFT JOIN documents_state s ON s.document_id = d.document_id
         WHERE d.document_id = $1
           AND d.is_deleted = FALSE`,
        [documentId],
    )

    return result.rows[0] ?? null
}

const authenticateCollaboration = createCollaborationAuthenticator({
    jwksUrl: springJwksUrl,
    issuer: jwtIssuer,
    audience: jwtYjsAudience,
    isDocumentActive: async documentId =>
        Boolean(await getActiveDocument(documentId)),
})

const hocuspocus = new Hocuspocus({
    debounce: 500,
    maxDebounce: 2000,
    onAuthenticate: authenticateCollaboration,
    extensions: [
        new Database({
            fetch: async ({documentName}) => {
                const {documentId} = parseDocumentName(documentName)
                const result = await pool.query(
                    `SELECT s.yjs_state
                     FROM documents_state s
                              JOIN documents d ON d.document_id = s.document_id
                     WHERE s.document_id = $1
                       AND d.is_deleted = FALSE`,
                    [documentId],
                )

                if (result.rowCount === 0) {
                    throw new Error('존재하지 않거나 삭제된 문서입니다.')
                }

                return result.rows[0].yjs_state
            },
            store: async ({documentName, state}) => {
                const {documentId} = parseDocumentName(documentName)
                const binary = Buffer.from(state)
                const result = await pool.query(
                    `UPDATE documents_state s
                     SET yjs_state          = $2,
                         binary_size        = $3,
                         state_hash         = $4,
                         persisted_revision = persisted_revision + 1,
                         updated_at         = CURRENT_TIMESTAMP FROM documents d
                     WHERE s.document_id = $1
                       AND d.document_id = s.document_id
                       AND d.is_deleted = FALSE
                         RETURNING s.persisted_revision`,
                    [documentId, binary, binary.byteLength, hashState(binary)],
                )

                if (result.rowCount === 0) {
                    throw new Error('존재하지 않거나 삭제된 문서입니다.')
                }

                await pool.query(
                    `UPDATE documents
                     SET updated_at = CURRENT_TIMESTAMP
                     WHERE document_id = $1`,
                    [documentId],
                )
            },
        }),
    ],
})

const app = express()

app.disable('x-powered-by')
app.use(express.json({limit: '1mb'}))

function requireInternalToken(request, response, next) {
    const providedToken = request.get('X-Internal-Token') || ''
    const expected = Buffer.from(internalApiToken)
    const provided = Buffer.from(providedToken)

    if (
        expected.length !== provided.length
        || !crypto.timingSafeEqual(expected, provided)
    ) {
        return response.status(401).json({message: '내부 API 인증에 실패했습니다.'})
    }

    next()
}

app.get('/health', async (_request, response) => {
    try {
        await pool.query('SELECT 1')
        response.json({
            status: 'ok',
            server: 'postgres-crdt',
            database: 'postgresql',
        })
    } catch (error) {
        response.status(503).json({
            status: 'unavailable',
            server: 'postgres-crdt',
            database: 'postgresql',
        })
    }
})

app.get('/api/documents', async (request, response, next) => {
    try {
        const values = []
        let teamFilter = ''

        if (request.query.teamId !== undefined) {
            values.push(request.query.teamId)
            teamFilter = `AND d.team_id = $${values.length}`
        }

        const result = await pool.query(
            `SELECT d.*, COALESCE(s.persisted_revision, 0) AS persisted_revision
             FROM documents d
                      LEFT JOIN documents_state s ON s.document_id = d.document_id
             WHERE d.is_deleted = FALSE
                 ${teamFilter}
             ORDER BY d.updated_at DESC`,
            values,
        )

        response.json(result.rows.map(mapDocument))
    } catch (error) {
        next(error)
    }
})

app.get('/api/documents/:id', async (request, response, next) => {
    try {
        const document = await getActiveDocument(request.params.id)

        if (!document) {
            return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
        }

        response.json(mapDocument(document))
    } catch (error) {
        next(error)
    }
})

app.post('/internal/documents', requireInternalToken, async (request, response, next) => {
    const teamId = Number(request.body.teamId)

    if (!Number.isSafeInteger(teamId) || teamId <= 0) {
        return response.status(400).json({message: '올바른 teamId가 필요합니다.'})
    }

    const client = await pool.connect()

    try {
        await client.query('BEGIN')

        const documentId = crypto.randomUUID()
        const emptyState = createEmptyYjsState()
        const inserted = await client.query(
            `INSERT INTO documents (document_id,
                                    team_id)
             VALUES ($1, $2) RETURNING *`,
            [documentId, teamId],
        )

        await client.query(
            `INSERT INTO documents_state (document_id,
                                          yjs_state,
                                          binary_size,
                                          state_hash)
             VALUES ($1, $2, $3, $4)`,
            [
                documentId,
                emptyState,
                emptyState.byteLength,
                hashState(emptyState),
            ],
        )

        await client.query('COMMIT')

        response.status(201).json(mapDocument({
            ...inserted.rows[0],
            persisted_revision: 0,
        }))
    } catch (error) {
        await client.query('ROLLBACK')
        next(error)
    } finally {
        client.release()
    }
})

app.delete('/api/documents/:id', async (request, response, next) => {
    try {
        const result = await pool.query(
            `UPDATE documents
             SET is_deleted = TRUE,
                 deleted_at = CURRENT_TIMESTAMP,
                 updated_at = CURRENT_TIMESTAMP
             WHERE document_id = $1
               AND is_deleted = FALSE RETURNING document_id`,
            [request.params.id],
        )

        if (result.rowCount === 0) {
            return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
        }

        hocuspocus.closeConnections(
            `document:${request.params.id}:epoch:1`,
        )

        response.status(204).end()
    } catch (error) {
        next(error)
    }
})

app.get('/api/documents/:id/versions', async (request, response, next) => {
    try {
        const document = await getActiveDocument(request.params.id)

        if (!document) {
            return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
        }

        const result = await pool.query(
            `SELECT *
             FROM documents_version
             WHERE document_id = $1
             ORDER BY version_number DESC`,
            [request.params.id],
        )

        response.json(result.rows.map(mapVersion))
    } catch (error) {
        next(error)
    }
})

app.post('/api/documents/:id/versions', async (request, response, next) => {
    const triggerType = typeof request.body.triggerType === 'string'
        ? request.body.triggerType.trim()
        : 'manual'
    const editorJson = request.body.editorJson ?? {}
    const requestId = request.body.requestId ?? null
    const client = await pool.connect()

    try {
        await client.query('BEGIN')

        const documentResult = await client.query(
            `SELECT *
             FROM documents
             WHERE document_id = $1
               AND is_deleted = FALSE
                 FOR UPDATE`,
            [request.params.id],
        )

        if (documentResult.rowCount === 0) {
            await client.query('ROLLBACK')
            return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
        }

        const stateResult = await client.query(
            `SELECT *
             FROM documents_state
             WHERE document_id = $1
                 FOR UPDATE`,
            [request.params.id],
        )
        const nextVersion = documentResult.rows[0].final_version + 1
        const versionId = crypto.randomUUID()
        const state = stateResult.rows[0]
        const inserted = await client.query(
            `INSERT INTO documents_version (document_version_id,
                                            document_id,
                                            version_number,
                                            title_snapshot,
                                            yjs_state,
                                            editor_json,
                                            trigger_type,
                                            binary_size,
                                            state_hash,
                                            schema_version,
                                            request_id)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING *`,
            [
                versionId,
                request.params.id,
                nextVersion,
                documentResult.rows[0].document_title,
                state.yjs_state,
                editorJson,
                triggerType,
                state.binary_size,
                state.state_hash,
                state.schema_version,
                requestId,
            ],
        )

        await client.query(
            `UPDATE documents
             SET final_version = $2,
                 updated_at    = CURRENT_TIMESTAMP
             WHERE document_id = $1`,
            [request.params.id, nextVersion],
        )
        await client.query('COMMIT')

        response.status(201).json(mapVersion(inserted.rows[0]))
    } catch (error) {
        await client.query('ROLLBACK')

        if (error.code === '23505' && requestId) {
            const existing = await pool.query(
                `SELECT *
                 FROM documents_version
                 WHERE document_id = $1
                   AND request_id = $2`,
                [request.params.id, requestId],
            )

            if (existing.rowCount > 0) {
                return response.json(mapVersion(existing.rows[0]))
            }
        }

        next(error)
    } finally {
        client.release()
    }
})

app.post(
    '/api/documents/:id/versions/:versionId/restore',
    async (request, response, next) => {
        const documentName = `document:${request.params.id}:epoch:1`
        const activeDocument = hocuspocus.documents.get(documentName)

        hocuspocus.flushPendingStores()
        hocuspocus.closeConnections(documentName)

        if (activeDocument && hocuspocus.documents.has(documentName)) {
            await hocuspocus.unloadDocument(activeDocument)
        }

        const client = await pool.connect()

        try {
            await client.query('BEGIN')

            const documentResult = await client.query(
                `SELECT *
                 FROM documents
                 WHERE document_id = $1
                   AND is_deleted = FALSE
                     FOR UPDATE`,
                [request.params.id],
            )

            if (documentResult.rowCount === 0) {
                await client.query('ROLLBACK')
                return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
            }

            const versionResult = await client.query(
                `SELECT *
                 FROM documents_version
                 WHERE document_version_id = $1
                   AND document_id = $2`,
                [request.params.versionId, request.params.id],
            )

            if (versionResult.rowCount === 0) {
                await client.query('ROLLBACK')
                return response.status(404).json({message: '버전을 찾을 수 없습니다.'})
            }

            const target = versionResult.rows[0]

            await client.query(
                `UPDATE documents_state
                 SET yjs_state          = $2,
                     binary_size        = $3,
                     state_hash         = $4,
                     schema_version     = $5,
                     persisted_revision = persisted_revision + 1,
                     updated_at         = CURRENT_TIMESTAMP
                 WHERE document_id = $1`,
                [
                    request.params.id,
                    target.yjs_state,
                    target.binary_size,
                    target.state_hash,
                    target.schema_version,
                ],
            )
            await client.query(
                `UPDATE documents
                 SET document_title = $2,
                     updated_at     = CURRENT_TIMESTAMP
                 WHERE document_id = $1`,
                [request.params.id, target.title_snapshot],
            )
            await client.query('COMMIT')

            response.json({
                restoredVersionId: target.document_version_id,
                versionNumber: target.version_number,
                stateEpoch: 1,
            })
        } catch (error) {
            await client.query('ROLLBACK')
            next(error)
        } finally {
            client.release()
        }
    },
)

app.use((error, _request, response, _next) => {
    console.error(error)

    if (error.code === '23503') {
        return response.status(400).json({
            message: '존재하지 않는 팀 또는 참조 대상입니다.',
        })
    }

    if (error.code === '22P02') {
        return response.status(400).json({message: '요청 값 형식이 올바르지 않습니다.'})
    }

    response.status(500).json({message: '서버 내부 오류가 발생했습니다.'})
})

const server = http.createServer(app)
const webSocketServer = new WebSocketServer({noServer: true})

webSocketServer.on('connection', (socket, request) => {
    const connection = hocuspocus.handleConnection(socket, request)

    socket.on('message', data => {
        connection.handleMessage(new Uint8Array(data))
    })
    socket.on('close', (code, reason) => {
        connection.handleClose({
            code,
            reason: reason.toString(),
        })
    })
    socket.on('error', error => {
        console.error('WebSocket 오류:', error)
    })
})

server.on('upgrade', (request, socket, head) => {
    if (!request.url?.startsWith('/collaboration')) {
        socket.destroy()
        return
    }

    webSocketServer.handleUpgrade(request, socket, head, webSocket => {
        webSocketServer.emit('connection', webSocket, request)
    })
})

server.listen(port, '0.0.0.0', () => {
    console.log(`PostgreSQL CRDT 서버가 ${port}번 포트에서 실행 중입니다.`)
})

async function shutdown() {
    server.close(async () => {
        await hocuspocus.destroy()
        await pool.end()
        process.exit(0)
    })
}

process.once('SIGTERM', shutdown)
process.once('SIGINT', shutdown)

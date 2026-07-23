import express from 'express'
import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'
import {fileURLToPath} from 'node:url'
import * as Y from 'yjs'
import {Hocuspocus} from '@hocuspocus/server'
import {Database} from '@hocuspocus/extension-database'
import {WebSocketServer} from "ws";

const port = Number(process.env.PORT || 3000)
const rootDir = path.dirname(fileURLToPath(import.meta.url))
const dataDir = path.join(rootDir, 'data')
const stateDir = path.join(dataDir, 'states')
const versionDir = path.join(dataDir, 'versions')
const documentMetadataFile = path.join(dataDir, 'documents.json')

async function readJson(file, fallback) {
    try {
        const text = await fs.readFile(file, 'utf8')

        return JSON.parse(text)
    } catch (error) {
        if (error.code === 'ENOENT') {
            return fallback
        }

        throw error
    }
}

async function writeJsonAtomic(file, val) {
    const tempFile = `${file}.tmp`
    const text = `${JSON.stringify(val, null, 2)}\n`

    try {
        await fs.writeFile(tempFile, text, 'utf8')

        await fs.rename(tempFile, file)
    } catch (error) {
        await fs.rm(tempFile, {force: true}).catch(() => {
        })

        throw error
    }

}

async function initializeStorage() {
    await fs.mkdir(dataDir, {recursive: true})
    await fs.mkdir(stateDir, {recursive: true})
    await fs.mkdir(versionDir, {recursive: true})

    const documents = await readJson(documentMetadataFile, null);

    if (documents === null) {
        await writeJsonAtomic(documentMetadataFile, [])
    } else if (!Array.isArray(documents)) {
        throw new Error('documents.json의 최상위 값은 배열이어야 합니다')
    }

}

async function getDocuments() {
    const documents = await readJson(documentMetadataFile, [])

    if (!Array.isArray(documents)) {
        throw new Error('documents.json의 최상위 값은 배열이어야만 합니다')
    }
    return documents
}

async function saveDocuments(documents) {
    await writeJsonAtomic(documentMetadataFile, documents)
}

function createEmptyYjsState() {
    const ydoc = new Y.Doc()

    try {
        const update = Y.encodeStateAsUpdate(ydoc)

        return Buffer.from(update)
    } finally {
        ydoc.destroy()
    }
}

function getDocumentStateFile(documentId) {
    return path.join(stateDir, `${documentId}.bin`)
}

async function writeBinaryAtomic(file, state) {
    const tempFile = `${file}.tmp`

    const binary = Buffer.from(state)

    try {
        await fs.writeFile(tempFile, binary)

        await fs.rename(tempFile, file)
    } catch (error) {
        await fs.rm(tempFile, {force: true}).catch(() => {
        })
        throw error
    }
}

async function readDocumentState(documentId) {
    return fs.readFile(getDocumentStateFile(documentId))
}

async function writeDocumentState(documentId, state) {
    await writeBinaryAtomic(getDocumentStateFile(documentId), state)
}

function parseDocumentName(documentName) {
    const match = /^document:([0-9a-f-]{36}):epoch:(\d+)$/i.exec(documentName)

    if (!match) {
        throw new Error(`잘못된 문서 연결 이름입니다: ${documentName}`)
    }

    return {
        documentId: match[1],
        epoch: Number(match[2]),
    }
}

const hocuspocus = new Hocuspocus({
    debounce: 500,
    maxDebounce: 2000,

    extensions: [
        new Database({
            fetch: async ({documentName}) => {
                const {documentId, epoch} = parseDocumentName(documentName)
                const documents = await getDocuments()
                const document = documents.find(item => item.id === documentId)

                if (!document) {
                    throw new Error('존재하지 않는 문서입니다.')
                }

                if (document.stateEpoch !== epoch) {
                    throw new Error('오래된 문서 세대입니다. 새로고침이 필요합니다.')
                }

                return readDocumentState(documentId)
            },

            store: async ({documentName, state}) => {
                const {documentId, epoch} = parseDocumentName(documentName)
                const documents = await getDocuments()
                const document = documents.find(item => item.id === documentId)

                if (!document) {
                    throw new Error('존재하지 않는 문서입니다.')
                }

                if (document.stateEpoch !== epoch) {
                    throw new Error('오래된 문서 세대의 저장 요청입니다.')
                }

                await writeDocumentState(documentId, state)
            },
        }),
    ],
})

await initializeStorage()

const app = express()

app.disable('x-powered-by')
app.use(express.json({limit: '64kb'}))
app.get('/health', (_request, response) => {
    response.json({
        status: 'ok',
        server: 'file-crdt',
    })
})

app.get('/api/documents', async (_request, response) => {
    const documents = await getDocuments()

    const sortedDocuments = [...documents].sort((left, right) => {
        return right.updatedAt.localeCompare(left.updatedAt)
    })

    response.json(sortedDocuments)
})

app.get('/api/documents/:id', async (request, response) => {
    const documents = await getDocuments()
    const document = documents.find(item => item.id === request.params.id)

    if (!document) {
        return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
    }

    response.json(document)
})

app.post('/api/documents', async (request, response) => {
    const title = typeof request.body.title === 'string' ? request.body.title.trim() : ''
    const createdBy = typeof request.body.createdBy === 'string' ? request.body.createdBy.trim() : ''

    if (!title || title.length > 120) {
        return response.status(400).json({message: '제목은 1~120자로 입력해 주세요.'})
    }
    if (!createdBy || createdBy.length > 30) {
        return response.status(400).json({message: '생성자는 1~30자로 입력해 주세요.'})
    }

    const documents = await getDocuments()
    const now = new Date().toISOString()

    const document = {
        id: crypto.randomUUID(),
        title,
        createdBy,
        currentRevision: 0,
        stateEpoch: 1,
        createdAt: now,
        updatedAt: now,
    }

    await writeDocumentState(document.id, createEmptyYjsState())

    documents.push(document)
    await saveDocuments(documents)

    response.status(201).json(document)
})

app.delete('/api/documents/:id', async (request, response) => {
    const documents = await getDocuments()
    const remainingDocuments = documents.filter(item => item.id !== request.params.id)

    if (remainingDocuments.length === documents.length) {
        return response.status(404).json({message: '문서를 찾을 수 없습니다.'})
    }

    await saveDocuments(remainingDocuments)

    response.status(204).end()
})

const server = http.createServer(app)

const webSocketServer = new WebSocketServer({noServer: true})

webSocketServer.on('connection', (socket, request) => {
    const connection = hocuspocus.handleConnection(socket, request)

    socket.on('message', data => {
        connection.handleMessage(data)
    })

    socket.on('close', () => {
        connection.handleClose()
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
    console.log(`파일 기반 CRDT 서버 8단계가 ${port}번 포트에서 실행 중입니다.`)
})

async function shutdown() {
    server.close(async () => {
        await hocuspocus.destroy()

        process.exit(0)
    })
}

process.once('SIGTERM', shutdown)
process.once('SIGINT', shutdown)

import express from 'express'
import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'
import {fileURLToPath} from 'node:url'

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

async function getDocument() {
    const documents = await readJson(documentMetadataFile, [])

    if (Array.isArray(documents)) {
        throw new Error('documents.json의 최상위 값은 배열이어야만 합니다')
    }
    return documents
}

async function saveDocuments(documents) {
    await writeJsonAtomic(documentMetadataFile, documents)
}

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
    const documents = await getDocument()

    const sortedDocuments = [...documents].sort((left, right) => {
        right.updateAt.localeCompare(left.updatedAt)
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

server.listen(port, '0.0.0.0', () => {
    console.log(`파일 기반 CRDT 서버 4단계가 ${port}번 포트에서 실행 중입니다.`)
})

async function shutdown() {
    server.close(() => {
        process.exit(0)
    })
}

process.once('SIGTERM', shutdown)
process.once('SIGINT', shutdown)

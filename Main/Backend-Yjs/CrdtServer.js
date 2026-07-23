import express from 'express'
import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'
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

const server = http.createServer(app)

server.listen(port, '0.0.0.0', () => {
    console.log(`파일 기반 CRDT 서버 2단계가 ${port}번 포트에서 실행 중입니다.`)
})

async function shutdown() {
    server.close(() => {
        process.exit(0)
    })
}

process.once('SIGTERM', shutdown)
process.once('SIGINT', shutdown)

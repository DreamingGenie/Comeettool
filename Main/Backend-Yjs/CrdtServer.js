import express from 'express'
import http from 'node:http'

const port = Number(process.env.PORT || 3000)
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
    console.log(`파일 기반 CRDT 서버 1단계가 ${port}번 포트에서 실행 중입니다.`)
})

async function shutdown() {
    server.close(() => {
        process.exit(0)
    })
}

process.once('SIGTERM', shutdown)
process.once('SIGINT', shutdown)

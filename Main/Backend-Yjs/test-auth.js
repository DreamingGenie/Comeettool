import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import test from 'node:test'
import {
    createLocalJWKSet,
    exportJWK,
    generateKeyPair,
    SignJWT,
} from 'jose'
import {createCollaborationAuthenticator} from './collaboration-auth.js'

const issuer = 'a707-api'
const audience = 'a707-yjs'
const keyId = 'test-rsa-key'
const documentId = crypto.randomUUID()
const documentName = `document:${documentId}:epoch:1`
const {privateKey, publicKey} = await generateKeyPair('RS256')
const publicJwk = {
    ...await exportJWK(publicKey),
    alg: 'RS256',
    kid: keyId,
    use: 'sig',
}
const keySet = createLocalJWKSet({keys: [publicJwk]})

async function createToken({
                               tokenAudience = audience,
                               tokenDocumentId = documentId,
                               permission = 'WRITE',
                               expirationTime = '5m',
                           } = {}) {
    return new SignJWT({
        type: 'collaboration',
        documentId: tokenDocumentId,
        teamId: 7,
        permission,
    })
        .setProtectedHeader({alg: 'RS256', kid: keyId})
        .setSubject('42')
        .setIssuer(issuer)
        .setAudience(tokenAudience)
        .setJti(crypto.randomUUID())
        .setIssuedAt()
        .setExpirationTime(expirationTime)
        .sign(privateKey)
}

function createAuthenticator(isDocumentActive = async () => true) {
    return createCollaborationAuthenticator({
        issuer,
        audience,
        keySet,
        isDocumentActive,
    })
}

test('WRITE 토큰을 검증하고 쓰기 가능한 연결 context를 반환한다', async () => {
    const connectionConfig = {readOnly: false}

    const context = await createAuthenticator()({
        token: await createToken(),
        documentName,
        connectionConfig,
    })

    assert.equal(connectionConfig.readOnly, false)
    assert.deepEqual(context, {
        userId: '42',
        documentId,
        teamId: 7,
        permission: 'WRITE',
        tokenId: context.tokenId,
    })
    assert.match(context.tokenId, /^[0-9a-f-]{36}$/)
})

test('READ 토큰은 Hocuspocus 연결을 읽기 전용으로 설정한다', async () => {
    const connectionConfig = {readOnly: false}

    const context = await createAuthenticator()({
        token: await createToken({permission: 'READ'}),
        documentName,
        connectionConfig,
    })

    assert.equal(connectionConfig.readOnly, true)
    assert.equal(context.permission, 'READ')
})

test('Yjs audience가 아닌 토큰은 거부한다', async () => {
    await assert.rejects(
        createAuthenticator()({
            token: await createToken({tokenAudience: 'a707-api'}),
            documentName,
            connectionConfig: {readOnly: false},
        }),
        /협업 토큰 인증에 실패했습니다/,
    )
})

test('토큰 문서와 연결 문서가 다르면 거부한다', async () => {
    await assert.rejects(
        createAuthenticator()({
            token: await createToken({tokenDocumentId: crypto.randomUUID()}),
            documentName,
            connectionConfig: {readOnly: false},
        }),
        /협업 토큰 인증에 실패했습니다/,
    )
})

test('허용 오차를 지난 만료 토큰은 거부한다', async () => {
    await assert.rejects(
        createAuthenticator()({
            token: await createToken({expirationTime: Math.floor(Date.now() / 1000) - 61}),
            documentName,
            connectionConfig: {readOnly: false},
        }),
        /협업 토큰 인증에 실패했습니다/,
    )
})

test('존재하지 않거나 삭제된 문서는 거부한다', async () => {
    await assert.rejects(
        createAuthenticator(async () => false)({
            token: await createToken(),
            documentName,
            connectionConfig: {readOnly: false},
        }),
        /협업 토큰 인증에 실패했습니다/,
    )
})
import {createRemoteJWKSet, jwtVerify} from 'jose'

const UUID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

function requireStringClaim(payload, claim) {
    const value = payload[claim]

    if (typeof value !== 'string' || value.length === 0) {
        throw new Error('협업 토큰 필수 클레임이 없습니다.')
    }

    return value
}

export function createCollaborationAuthenticator({
                                                     jwksUrl,
                                                     issuer = 'a707-api',
                                                     audience = 'a707-yjs',
                                                     keySet,
                                                     isDocumentActive,
                                                 }) {
    if (!keySet && !jwksUrl) {
        throw new Error('SPRING_JWKS_URL 환경변수가 필요합니다.')
    }

    if (typeof isDocumentActive !== 'function') {
        throw new Error('활성 문서 조회 함수가 필요합니다.')
    }

    const verificationKeySet = keySet ?? createRemoteJWKSet(new URL(jwksUrl))

    return async function authenticateCollaboration({
                                                        token,
                                                        documentName,
                                                        connectionConfig,
                                                    }) {
        try {
            const {payload, protectedHeader} = await jwtVerify(
                token,
                verificationKeySet,
                {
                    algorithms: ['RS256'],
                    issuer,
                    audience,
                    clockTolerance: 60,
                    requiredClaims: ['sub', 'jti', 'iat', 'exp'],
                },
            )

            if (typeof protectedHeader.kid !== 'string'
                || protectedHeader.kid.length === 0) {
                throw new Error('협업 토큰 kid가 없습니다.')
            }

            const documentId = requireStringClaim(payload, 'documentId')
            const permission = requireStringClaim(payload, 'permission')
            requireStringClaim(payload, 'sub')
            requireStringClaim(payload, 'jti')

            if (payload.type !== 'collaboration') {
                throw new Error('협업 토큰 타입이 올바르지 않습니다.')
            }

            if (!UUID_PATTERN.test(documentId)) {
                throw new Error('협업 토큰 문서 ID가 올바르지 않습니다.')
            }

            if (!Number.isSafeInteger(payload.teamId) || payload.teamId <= 0) {
                throw new Error('협업 토큰 팀 ID가 올바르지 않습니다.')
            }

            if (permission !== 'READ' && permission !== 'WRITE') {
                throw new Error('협업 토큰 권한이 올바르지 않습니다.')
            }

            const connectedDocumentId = parseDocumentName(documentName).documentId

            if (documentId.toLowerCase() !== connectedDocumentId.toLowerCase()) {
                throw new Error('협업 토큰의 문서가 연결 대상과 다릅니다.')
            }

            if (!await isDocumentActive(documentId)) {
                throw new Error('존재하지 않거나 삭제된 문서입니다.')
            }

            connectionConfig.readOnly = permission === 'READ'

            return {
                userId: payload.sub,
                documentId,
                teamId: payload.teamId,
                permission,
                tokenId: payload.jti,
            }
        } catch {
            throw new Error('협업 토큰 인증에 실패했습니다.')
        }
    }
}

export function parseDocumentName(documentName) {
    const match = /^document:([0-9a-f-]{36}):epoch:(\d+)$/i.exec(documentName)

    if (!match || !UUID_PATTERN.test(match[1])) {
        throw new Error('잘못된 문서 연결 이름입니다.')
    }

    const epoch = Number(match[2])

    if (epoch !== 1) {
        throw new Error('지원하지 않는 문서 세대입니다. 문서 정보를 새로 조회해주세요.')
    }

    return {
        documentId: match[1],
        epoch,
    }
}
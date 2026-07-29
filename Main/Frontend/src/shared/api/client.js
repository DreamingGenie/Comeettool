import { authSession } from './authSession'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
let refreshPromise = null

function createApiError(response, payload) {
  const error = new Error(
    payload?.message || payload?.error || '요청을 처리하지 못했습니다.'
  )
  error.status = response.status
  error.code = payload?.code || ''
  error.payload = payload
  return error
}

async function parsePayload(response) {
  if (response.status === 204) return null
  return response.json().catch(() => null)
}

function expireSession() {
  authSession.clear()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('auth:expired'))
  }
}

async function refreshAccessToken() {
  if (refreshPromise) return refreshPromise

  const { refreshToken } = authSession.get()
  if (!refreshToken) throw new Error('로그인이 만료되었습니다.')

  refreshPromise = (async () => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/token/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    })
    const payload = await parsePayload(response)

    if (!response.ok || !payload?.data?.accessToken) {
      throw createApiError(response, payload)
    }

    authSession.updateAccessToken(payload.data.accessToken)
    return payload.data.accessToken
  })()

  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

export async function request(path, options = {}) {
  const {
    auth = true,
    retryOnUnauthorized = true,
    unwrap = true,
    ...fetchOptions
  } = options
  const token = authSession.get().accessToken
  const headers = {
    ...(fetchOptions.body instanceof FormData
      ? {}
      : { 'Content-Type': 'application/json' }),
    ...(auth && token ? { Authorization: `Bearer ${token}` } : {}),
    ...fetchOptions.headers
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...fetchOptions,
    headers
  })
  const payload = await parsePayload(response)

  if (response.status === 401 && auth) {
    if (retryOnUnauthorized) {
      try {
        await refreshAccessToken()
        return request(path, { ...options, retryOnUnauthorized: false })
      } catch {
        expireSession()
      }
    } else {
      expireSession()
    }
  }

  if (!response.ok) throw createApiError(response, payload)
  if (unwrap && payload && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    return payload.data
  }
  return payload
}

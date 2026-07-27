const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: 'include',
    ...options,
    headers: {
      ...(options.body instanceof FormData
        ? {}
        : { 'Content-Type': 'application/json' }),
      ...options.headers
    }
  })

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    const error = new Error(
      payload?.message || payload?.error || '요청을 처리하지 못했습니다.'
    )
    error.status = response.status
    error.payload = payload
    throw error
  }

  return payload
}

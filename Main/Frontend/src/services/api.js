const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers
    },
    ...options
  })

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(payload?.error || '요청을 처리하지 못했습니다.')
    error.status = response.status
    error.payload = payload
    throw error
  }
  return payload
}

export const authApi = {
  signup: data => request('/api/signup', { method: 'POST', body: JSON.stringify(data) }),
  login: data => request('/api/login', { method: 'POST', body: JSON.stringify(data) }),
  logout: () => request('/api/logout', { method: 'POST' }),
  me: () => request('/api/me'),
  changePassword: data => request('/api/password', { method: 'POST', body: JSON.stringify(data) })
}

export const roomApi = {
  list: () => request('/api/rooms'),
  create: data => request('/api/rooms', { method: 'POST', body: JSON.stringify(data) }),
  join: code => request(`/api/rooms/${code}/join`, { method: 'POST' }),
  leave: code => request(`/api/rooms/${code}/leave`, { method: 'POST' }),
  end: code => request(`/api/rooms/${code}/end`, { method: 'POST' }),
  messages: code => request(`/api/rooms/${code}/messages`),
  sendMessage: (code, body) => request(`/api/rooms/${code}/messages`, { method: 'POST', body: JSON.stringify({ body }) }),
  document: code => request(`/api/rooms/${code}/document`),
  saveDocument: (code, data) => request(`/api/rooms/${code}/document`, { method: 'PUT', body: JSON.stringify(data) })
}

export const scheduleApi = {
  history: () => request('/api/history'),
  events: () => request('/api/events'),
  createEvent: data => request('/api/events', { method: 'POST', body: JSON.stringify(data) })
}

export { request }

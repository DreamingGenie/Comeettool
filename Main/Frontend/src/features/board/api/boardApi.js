import { request } from '../../../shared/api'

export const boardApi = {
  listRooms: () => request('/api/rooms'),
  createRoom: data =>
    request('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  joinRoom: code => request(`/api/rooms/${code}/join`, { method: 'POST' }),
  leaveRoom: code => request(`/api/rooms/${code}/leave`, { method: 'POST' }),
  endRoom: code => request(`/api/rooms/${code}/end`, { method: 'POST' }),
  getMessages: code => request(`/api/rooms/${code}/messages`),
  sendMessage: (code, body) =>
    request(`/api/rooms/${code}/messages`, {
      method: 'POST',
      body: JSON.stringify({ body })
    }),
  getDocument: code => request(`/api/rooms/${code}/document`),
  saveDocument: (code, data) =>
    request(`/api/rooms/${code}/document`, {
      method: 'PUT',
      body: JSON.stringify(data)
    }),
  getHistory: () => request('/api/history'),
  getEvents: () => request('/api/events'),
  createEvent: data =>
    request('/api/events', {
      method: 'POST',
      body: JSON.stringify(data)
    })
}

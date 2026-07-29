const SESSION_KEY = 'comeet.auth.session'

const getStorage = () =>
  typeof window === 'undefined' ? null : window.sessionStorage

export function loadAuthSession() {
  const storage = getStorage()
  if (!storage) return null

  try {
    return JSON.parse(storage.getItem(SESSION_KEY)) || null
  } catch {
    storage.removeItem(SESSION_KEY)
    return null
  }
}

export function saveAuthSession(session) {
  getStorage()?.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearAuthSession() {
  getStorage()?.removeItem(SESSION_KEY)
}

const STORAGE_KEY = 'comeet.auth.session'

const emptySession = () => ({
  accessToken: '',
  refreshToken: '',
  userId: null,
  onboarded: false
})

function getStorage() {
  if (typeof window === 'undefined') return null

  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function readSession() {
  const storage = getStorage()
  if (!storage) return emptySession()

  try {
    const stored = JSON.parse(storage.getItem(STORAGE_KEY) || '{}')
    return {
      accessToken: stored.accessToken || '',
      refreshToken: stored.refreshToken || '',
      userId: stored.userId ?? null,
      onboarded: Boolean(stored.onboarded)
    }
  } catch {
    storage.removeItem(STORAGE_KEY)
    return emptySession()
  }
}

function writeSession(session) {
  const storage = getStorage()
  if (!storage) return
  storage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export const authSession = {
  get() {
    return readSession()
  },
  save(session) {
    writeSession({
      accessToken: session.accessToken || '',
      refreshToken: session.refreshToken || '',
      userId: session.userId ?? null,
      onboarded: Boolean(session.onboarded)
    })
  },
  updateAccessToken(accessToken) {
    const session = readSession()
    writeSession({ ...session, accessToken: accessToken || '' })
  },
  updateTokens({ accessToken, refreshToken }) {
    const session = readSession()
    writeSession({
      ...session,
      accessToken: accessToken || session.accessToken,
      refreshToken: refreshToken || session.refreshToken
    })
  },
  updateOnboarded(onboarded) {
    const session = readSession()
    writeSession({ ...session, onboarded: Boolean(onboarded) })
  },
  clear() {
    getStorage()?.removeItem(STORAGE_KEY)
  },
  hasAccessToken() {
    return Boolean(readSession().accessToken)
  }
}

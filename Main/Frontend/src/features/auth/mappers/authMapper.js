const unwrapData = response => response?.data ?? response ?? {}

const getOnboarded = data =>
  typeof data?.onboarded === 'boolean' ? data.onboarded : false

export function mapLoginResponse(response, email) {
  const data = unwrapData(response)
  const onboarded = getOnboarded(data)

  return {
    code: response?.code,
    message: response?.message,
    user: {
      id: data.userId,
      email,
      onboarded
    },
    tokenType: data.tokenType || 'Bearer',
    accessToken: data.accessToken || '',
    refreshToken: data.refreshToken || '',
    onboarded
  }
}

export function mapSignupResponse(response) {
  const data = unwrapData(response)
  return {
    code: response?.code,
    message: response?.message,
    user: {
      id: data.userId,
      email: data.email,
      createdAt: data.createdAt
    }
  }
}

export function mapTokenRefreshResponse(response) {
  const data = unwrapData(response)
  return {
    code: response?.code,
    message: response?.message,
    tokenType: data.tokenType || 'Bearer',
    accessToken: data.accessToken || ''
  }
}

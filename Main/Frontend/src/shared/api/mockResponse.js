export const cloneMockValue = value => {
  if (value === null || value === undefined || typeof value !== 'object') {
    return value
  }

  return JSON.parse(JSON.stringify(value))
}

export const mockResponse = value => Promise.resolve(cloneMockValue(value))

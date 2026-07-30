const now = Date.now()

export const documentMockDatabase = {
  documents: [
    {
      documentId: '11111111-1111-4111-8111-111111111111',
      teamId: 'a707',
      title: 'API 연동 체크리스트',
      finalVersion: 0,
      stateEpoch: 1,
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 3).toISOString(),
      updatedAt: new Date(now - 1000 * 60 * 5).toISOString(),
      deleted: false
    },
    {
      documentId: '22222222-2222-4222-8222-222222222222',
      teamId: 'a707',
      title: '7월 스프린트 백로그',
      finalVersion: 0,
      stateEpoch: 1,
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 7).toISOString(),
      updatedAt: new Date(now - 1000 * 60 * 18).toISOString(),
      deleted: false
    }
  ]
}
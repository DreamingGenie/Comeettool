import { beforeEach, describe, expect, it, vi } from 'vitest'

// dataSource를 목으로 대체해 실제 네트워크/환경 의존 없이 store 로직만 검증한다.
vi.mock('../../../shared/api/dataSource', () => ({
  dataSource: {
    board: {
      joinMeeting: vi.fn(),
      getParticipants: vi.fn()
    }
  }
}))

const { dataSource } = await import('../../../shared/api/dataSource')
const { boardStore } = await import('./boardStore')
const { toMeetingConnectionViewModel } = await import('../mappers/meetingMapper')

describe('회의 입장 teamId 보존 (새로고침 시 팀스페이스 유지)', () => {
  beforeEach(() => {
    // 새로고침 직후 상태 재현: 회의 목록 비었고 팀스페이스 컨텍스트 없음.
    boardStore.clearMeetingRoom()
    boardStore.state.meetings = []
    boardStore.state.currentTeamId = ''
    boardStore.state.activeMeeting = { id: '', teamId: '' }
    vi.clearAllMocks()
  })

  it('커넥션 매퍼가 teamId를 포함한다', () => {
    const vm = toMeetingConnectionViewModel({
      meetingRoomId: 34,
      teamId: 7,
      role: 'BE',
      isHost: true,
      token: 't',
      url: 'ws://x'
    })
    expect(vm.teamId).toBe('7')
  })

  it('회의 목록에 없어도(새로고침) join 응답 teamId로 currentTeamId·activeMeeting.teamId를 복원한다', async () => {
    dataSource.board.joinMeeting.mockResolvedValue({
      meetingId: '34',
      teamId: '7',
      role: 'BE',
      isHost: true,
      token: 't',
      url: 'ws://x'
    })
    dataSource.board.getParticipants.mockResolvedValue([])

    await boardStore.loadMeetingRoom('34')

    expect(boardStore.state.currentTeamId).toBe('7')
    expect(boardStore.state.activeMeeting.teamId).toBe('7')
    expect(boardStore.state.activeMeeting.id).toBe('34')
  })

  it('teamId가 없으면 팀스페이스 컨텍스트를 덮어쓰지 않는다', async () => {
    dataSource.board.joinMeeting.mockResolvedValue({
      meetingId: '34',
      teamId: '',
      role: 'BE',
      isHost: true,
      token: 't',
      url: 'ws://x'
    })
    dataSource.board.getParticipants.mockResolvedValue([])

    await boardStore.loadMeetingRoom('34')

    expect(boardStore.state.currentTeamId).toBe('')
  })
})

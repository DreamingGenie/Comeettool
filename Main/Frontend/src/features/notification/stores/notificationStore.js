import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({
  invitations: [],
  // 진행 중인 회의 초대. 스페이스 초대와 달리 거절이 없고 "바로 참가하기" 하나뿐이다.
  meetingInvites: [],
  loading: false,
  acceptingId: '',
  rejectingId: '',
  error: ''
})

export const notificationStore = {
  state,
  async loadInvitations() {
    if (state.loading) return state.invitations
    state.loading = true
    state.error = ''
    try {
      const invitations = await dataSource.notification.getInvitations()
      state.invitations = Array.isArray(invitations) ? invitations : []
      return state.invitations
    } catch (error) {
      state.error = error?.message || '초대 알림을 불러오지 못했습니다.'
      throw error
    } finally {
      state.loading = false
    }
  },
  /**
   * 회의 초대 알림 목록을 만든다.
   *
   * 별도 엔드포인트를 만들지 않고 MEET-02(`GET /spaces/{spaceId}/meetings`)를 스페이스별로 모은다.
   * 그 API가 "내가 Participant로 등록된 진행 중 회의"만 돌려주는데, 회의 초대(MEET-10)가 하는 일이
   * 바로 Participant 행 생성이므로 그 목록이 곧 초대 목록이다.
   */
  async loadMeetingInvites({ spaces = [], excludeMeetingId = '' } = {}) {
    const spaceList = Array.isArray(spaces) ? spaces.filter(space => space?.id) : []
    if (!spaceList.length) {
      state.meetingInvites = []
      return state.meetingInvites
    }

    const results = await Promise.all(
      spaceList.map(async space => {
        try {
          const meetings = await dataSource.board.getMeetings(space.id)
          return (Array.isArray(meetings) ? meetings : []).map(meeting => ({
            ...meeting,
            spaceName: space.name || ''
          }))
        } catch {
          // 스페이스 하나가 실패해도 나머지 알림은 보여준다.
          return []
        }
      })
    )

    state.meetingInvites = results
      .flat()
      .filter(meeting => {
        if (!meeting?.id) return false
        // 지금 들어가 있는 회의는 알릴 필요가 없다.
        if (excludeMeetingId && String(meeting.id) === String(excludeMeetingId)) return false
        // 이미 접속 중이면 제외. webhook 연결 전에는 isInMeeting이 항상 false라
        // 실질 효과가 없지만, 연결되면 그때부터 정확해진다.
        return !meeting.isInMeeting
      })
      .map(meeting => ({
        meetingId: String(meeting.id),
        teamId: String(meeting.teamId || ''),
        spaceName: meeting.spaceName,
        title: meeting.roomTitle || meeting.title || '회의',
        participantCount: Number(meeting.participantCount ?? 0),
        createdAt: meeting.createdAt || null
      }))

    return state.meetingInvites
  },
  // 참가 버튼을 누른 직후 목록에서 즉시 뺀다. 회의가 계속 진행 중이면 다음 폴링에서 다시 올라온다.
  dismissMeetingInvite(meetingId) {
    state.meetingInvites = state.meetingInvites.filter(
      invite => String(invite.meetingId) !== String(meetingId)
    )
  },
  async acceptInvitation(invitationId) {
    state.acceptingId = invitationId
    state.error = ''
    try {
      await dataSource.notification.acceptInvitation(invitationId)
      state.invitations = state.invitations.filter(
        (invitation) => invitation.invitationId !== invitationId
      )
    } catch (error) {
      state.error = error?.message || '초대를 수락하지 못했습니다.'
      throw error
    } finally {
      state.acceptingId = ''
    }
  },
  async rejectInvitation(invitationId) {
    state.rejectingId = invitationId
    state.error = ''
    try {
      await dataSource.notification.rejectInvitation(invitationId)
      state.invitations = state.invitations.filter(
        (invitation) => invitation.invitationId !== invitationId
      )
    } catch (error) {
      state.error = error?.message || '초대를 거절하지 못했습니다.'
      throw error
    } finally {
      state.rejectingId = ''
    }
  },
  reset() {
    state.invitations = []
    state.meetingInvites = []
    state.loading = false
    state.acceptingId = ''
    state.rejectingId = ''
    state.error = ''
  }
}

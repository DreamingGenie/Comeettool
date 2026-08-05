import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({
  invitations: [],
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
          return []
        }
      })
    )

    state.meetingInvites = results
      .flat()
      .filter(meeting => {
        if (!meeting?.id) return false
        if (excludeMeetingId && String(meeting.id) === String(excludeMeetingId)) return false
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

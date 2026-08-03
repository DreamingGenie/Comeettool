import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({
  invitations: [],
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
    state.loading = false
    state.acceptingId = ''
    state.rejectingId = ''
    state.error = ''
  }
}

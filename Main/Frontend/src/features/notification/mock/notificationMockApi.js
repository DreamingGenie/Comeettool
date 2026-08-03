const invitations = [
  {
    invitationId: 'mock-invitation-a707',
    spaceId: 707,
    spaceName: 'A707',
    inviterNickname: '전진',
    createdAt: new Date().toISOString()
  }
]

export const notificationMockApi = {
  async getInvitations() {
    return invitations.map(invitation => ({ ...invitation }))
  },
  async acceptInvitation(invitationId) {
    const index = invitations.findIndex(
      invitation => invitation.invitationId === invitationId
    )
    if (index >= 0) invitations.splice(index, 1)
    return null
  }
}


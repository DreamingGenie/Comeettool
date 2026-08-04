const initials = value => {
  const name = String(value || '').trim()
  if (!name) return '?'

  const words = name.split(/\s+/)
  if (words.length > 1) {
    return words
      .slice(0, 2)
      .map(word => word[0])
      .join('')
      .toUpperCase()
  }

  return name.slice(0, 2).toUpperCase()
}

const formatCreatedAt = value => {
  if (!value) return { date: '', time: '', agendaTime: '' }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return { date: '', time: '', agendaTime: '' }
  }

  return {
    date: new Intl.DateTimeFormat('ko-KR', {
      month: 'long',
      day: 'numeric'
    }).format(date),
    time: new Intl.DateTimeFormat('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(date),
    agendaTime: new Intl.DateTimeFormat('ko-KR', {
      hour: 'numeric',
      minute: '2-digit'
    }).format(date)
  }
}

export const toMeetingViewModel = (meeting, fallback = {}) => {
  const meetingRoomId = meeting?.meetingRoomId ?? meeting?.id ?? fallback.id
  const meetingRoomName =
    meeting?.meetingRoomName ??
    meeting?.title ??
    fallback.meetingRoomName ??
    fallback.title ??
    '회의'
  const createdAt = meeting?.createdAt ?? fallback.createdAt
  const formatted = formatCreatedAt(createdAt)

  return {
    id: String(meetingRoomId ?? ''),
    teamId: String(meeting?.teamId ?? fallback.teamId ?? ''),
    title: meetingRoomName,
    roomTitle: meetingRoomName,
    status: 'LIVE',
    description: '진행 중인 회의',
    createdAt: createdAt || null,
    date: formatted.date,
    time: formatted.time,
    agendaTime: formatted.agendaTime,
    participantCount: Number(meeting?.participantCount ?? 0),
    avatars: [],
    hostId: meeting?.host?.userId ?? fallback.hostId ?? null,
    isInMeeting: Boolean(meeting?.isInMeeting)
  }
}

export const toMeetingListViewModel = meetings =>
  (Array.isArray(meetings) ? meetings : []).map(meeting =>
    toMeetingViewModel(meeting)
  )

export const toMeetingConnectionViewModel = connection => ({
  meetingId: String(connection?.meetingRoomId ?? ''),
  role: connection?.role || '',
  isHost: Boolean(connection?.isHost),
  token: connection?.token || '',
  url: connection?.url || ''
})

export const toMeetingParticipantViewModel = participant => {
  const name = participant?.nickname || `사용자 ${participant?.userId ?? ''}`
  const isHost = Boolean(participant?.isHost)
  const isInMeeting = Boolean(participant?.isInMeeting)

  return {
    id: String(participant?.participantId ?? ''),
    participantId: participant?.participantId ?? null,
    livekitIdentity: participant?.participantId
      ? `participant-${participant.participantId}`
      : '',
    memberId: participant?.memberId ?? null,
    userId: participant?.userId ?? null,
    name,
    displayName: name,
    avatarText: initials(name),
    profileImageUrl: participant?.profileImageUrl || '',
    role: isHost ? '호스트' : participant?.participantRole || '참여자',
    participantRole: participant?.participantRole || '',
    status: isInMeeting ? '회의 참여 중' : '연결 안 됨',
    isHost,
    isInMeeting,
    muted: false
  }
}

export const toMeetingParticipantsViewModel = participants =>
  (Array.isArray(participants) ? participants : []).map(
    toMeetingParticipantViewModel
  )

export const toMeetingInviteCandidateViewModel = candidate => {
  const name = candidate?.nickname || candidate?.email || `사용자 ${candidate?.userId ?? ''}`

  return {
    id: String(candidate?.memberId ?? candidate?.userId ?? ''),
    memberId: candidate?.memberId ?? null,
    userId: candidate?.userId ?? null,
    name,
    email: candidate?.email || '',
    avatarText: initials(name),
    profileImageUrl: candidate?.profileImage || ''
  }
}

export const toMeetingInviteCandidatesViewModel = candidates =>
  (Array.isArray(candidates) ? candidates : []).map(
    toMeetingInviteCandidateViewModel
  )

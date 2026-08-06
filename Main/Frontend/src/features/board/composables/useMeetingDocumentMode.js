import { reactive } from 'vue'

const state = reactive({
  active: false,
  meetingId: '',
  teamId: '',
  returnRoute: ''
})

function begin({ meetingId, teamId, returnRoute }) {
  state.active = true
  state.meetingId = String(meetingId || '')
  state.teamId = String(teamId || '')
  state.returnRoute = String(returnRoute || '')
}

function clear() {
  state.active = false
  state.meetingId = ''
  state.teamId = ''
  state.returnRoute = ''
}

function isActiveMeeting(meetingId) {
  return state.active && state.meetingId === String(meetingId || '')
}

export function findMeetingPictureInPictureVideo(root) {
  if (!root?.querySelector) return null

  const selectors = [
    '.video-tile.is-screen-share video.livekit-video',
    '.video-tile.is-pinned video.livekit-video',
    '.video-tile.is-speaking video.livekit-video',
    '.video-tile video.livekit-video'
  ]

  for (const selector of selectors) {
    const video = [...root.querySelectorAll(selector)].find(
      candidate => !candidate.disablePictureInPicture && candidate.readyState > 0
    )
    if (video) return video
  }

  return null
}

export const meetingDocumentMode = {
  state,
  begin,
  clear,
  isActiveMeeting
}

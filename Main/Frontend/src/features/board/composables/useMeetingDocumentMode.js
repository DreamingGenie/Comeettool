import { reactive } from 'vue'

const state = reactive({
  active: false,
  meetingId: '',
  teamId: '',
  returnRoute: ''
})

let persistentPictureInPictureVideo = null
let placeholderStream = null
let placeholderTimer = null

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

function allowMeetingAction(notify) {
  if (!state.active) return true

  notify?.('이미 회의에 참여 중입니다.')
  return false
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

function createPersistentVideoElement() {
  const video = document.createElement('video')
  video.muted = true
  video.autoplay = true
  video.playsInline = true
  video.disablePictureInPicture = false
  video.setAttribute('aria-hidden', 'true')
  Object.assign(video.style, {
    position: 'fixed',
    left: '-10000px',
    bottom: '0',
    width: '2px',
    height: '2px',
    opacity: '0.001',
    pointerEvents: 'none'
  })
  document.body.appendChild(video)
  return video
}

function createCameraOffStream() {
  const canvas = document.createElement('canvas')
  canvas.width = 640
  canvas.height = 360

  if (typeof canvas.captureStream !== 'function') {
    throw new Error('현재 브라우저에서는 카메라가 꺼진 상태의 PiP를 지원하지 않습니다.')
  }

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('PiP 안내 화면을 생성하지 못했습니다.')
  }

  const drawPlaceholder = () => {
    context.fillStyle = '#111214'
    context.fillRect(0, 0, canvas.width, canvas.height)

    context.beginPath()
    context.arc(canvas.width / 2, 132, 42, 0, Math.PI * 2)
    context.fillStyle = '#2d3035'
    context.fill()

    context.fillStyle = '#f5f6f8'
    context.font = '600 24px sans-serif'
    context.textAlign = 'center'
    context.fillText('카메라가 꺼져 있습니다', canvas.width / 2, 218)

    context.fillStyle = '#9da3ae'
    context.font = '400 16px sans-serif'
    context.fillText('회의 연결은 계속 유지됩니다', canvas.width / 2, 250)
  }

  const stream = canvas.captureStream(1)
  drawPlaceholder()
  placeholderTimer = window.setInterval(drawPlaceholder, 1000)
  return stream
}

function getActiveVideoTracks(sourceVideo) {
  const stream = sourceVideo?.srcObject
  if (typeof MediaStream === 'undefined' || !(stream instanceof MediaStream)) return []

  return stream
    .getVideoTracks()
    .filter(track => track.readyState === 'live' && track.enabled && !track.muted)
}

export async function createMeetingPictureInPictureVideo(sourceVideo = null) {
  releaseMeetingPictureInPictureVideo()

  const video = createPersistentVideoElement()
  persistentPictureInPictureVideo = video
  const videoTracks = getActiveVideoTracks(sourceVideo)

  if (videoTracks.length > 0) {
    video.srcObject = new MediaStream(videoTracks)
  } else {
    placeholderStream = createCameraOffStream()
    video.srcObject = placeholderStream
  }

  try {
    await video.play()
    return video
  } catch (error) {
    releaseMeetingPictureInPictureVideo()
    throw error
  }
}

export function releaseMeetingPictureInPictureVideo() {
  if (placeholderTimer) {
    window.clearInterval(placeholderTimer)
    placeholderTimer = null
  }

  placeholderStream?.getTracks().forEach(track => track.stop())
  placeholderStream = null

  if (persistentPictureInPictureVideo) {
    persistentPictureInPictureVideo.pause()
    persistentPictureInPictureVideo.srcObject = null
    persistentPictureInPictureVideo.remove()
    persistentPictureInPictureVideo = null
  }
}

export const meetingDocumentMode = {
  state,
  begin,
  clear,
  isActiveMeeting,
  allowMeetingAction
}

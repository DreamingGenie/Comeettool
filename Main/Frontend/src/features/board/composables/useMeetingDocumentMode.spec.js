import { afterEach, describe, expect, it } from 'vitest'
import { findMeetingPictureInPictureVideo, meetingDocumentMode } from './useMeetingDocumentMode'

afterEach(() => {
  meetingDocumentMode.clear()
  document.body.innerHTML = ''
})

describe('meetingDocumentMode', () => {
  it('회의와 문서 이동에 필요한 복귀 정보를 보관하고 초기화한다', () => {
    meetingDocumentMode.begin({
      meetingId: 12,
      teamId: 3,
      returnRoute: '/meetings/12'
    })

    expect(meetingDocumentMode.state).toMatchObject({
      active: true,
      meetingId: '12',
      teamId: '3',
      returnRoute: '/meetings/12'
    })
    expect(meetingDocumentMode.isActiveMeeting(12)).toBe(true)

    meetingDocumentMode.clear()

    expect(meetingDocumentMode.state).toMatchObject({
      active: false,
      meetingId: '',
      teamId: '',
      returnRoute: ''
    })
  })
})

describe('findMeetingPictureInPictureVideo', () => {
  const appendVideo = (className, readyState = 4) => {
    const tile = document.createElement('article')
    tile.className = `video-tile ${className}`
    const video = document.createElement('video')
    video.className = 'livekit-video'
    Object.defineProperty(video, 'readyState', {
      configurable: true,
      value: readyState
    })
    Object.defineProperty(video, 'disablePictureInPicture', {
      configurable: true,
      writable: true,
      value: false
    })
    tile.appendChild(video)
    document.body.appendChild(tile)
    return video
  }

  it('화면 공유 영상을 가장 먼저 선택한다', () => {
    appendVideo('is-speaking')
    const screenShare = appendVideo('is-screen-share')
    appendVideo('is-pinned')

    expect(findMeetingPictureInPictureVideo(document.body)).toBe(screenShare)
  })

  it('준비되지 않았거나 PiP가 비활성화된 영상은 제외한다', () => {
    const unavailable = appendVideo('is-screen-share', 0)
    unavailable.disablePictureInPicture = true
    const fallback = appendVideo('')

    expect(findMeetingPictureInPictureVideo(document.body)).toBe(fallback)
  })

  it('사용 가능한 영상이 없으면 null을 반환한다', () => {
    appendVideo('', 0)

    expect(findMeetingPictureInPictureVideo(document.body)).toBeNull()
  })
})

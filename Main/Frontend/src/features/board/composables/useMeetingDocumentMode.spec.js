import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createMeetingPictureInPictureVideo,
  findMeetingPictureInPictureVideo,
  meetingDocumentMode,
  releaseMeetingPictureInPictureVideo
} from './useMeetingDocumentMode'

class FakeMediaStream {
  constructor(tracks = []) {
    this.tracks = tracks
  }

  getVideoTracks() {
    return this.tracks.filter(track => track.kind === 'video')
  }

  getTracks() {
    return this.tracks
  }
}

beforeEach(() => {
  vi.stubGlobal('MediaStream', FakeMediaStream)
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined)
})

afterEach(() => {
  releaseMeetingPictureInPictureVideo()
  meetingDocumentMode.clear()
  document.body.innerHTML = ''
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('createMeetingPictureInPictureVideo', () => {
  it('회의 화면과 분리된 비디오에 활성 카메라 트랙을 연결한다', async () => {
    const cameraTrack = {
      kind: 'video',
      readyState: 'live',
      enabled: true,
      muted: false
    }
    const sourceVideo = document.createElement('video')
    sourceVideo.srcObject = new FakeMediaStream([cameraTrack])

    const pipVideo = await createMeetingPictureInPictureVideo(sourceVideo)

    expect(pipVideo).not.toBe(sourceVideo)
    expect(pipVideo.isConnected).toBe(true)
    expect(pipVideo.srcObject.getVideoTracks()).toEqual([cameraTrack])
  })

  it('카메라가 꺼져 있으면 안내 화면 스트림으로 PiP를 준비한다', async () => {
    const placeholderTrack = { kind: 'video', stop: vi.fn() }
    const placeholderStream = new FakeMediaStream([placeholderTrack])
    const context = {
      fillStyle: '',
      font: '',
      textAlign: '',
      fillRect: vi.fn(),
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      fillText: vi.fn()
    }
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context)
    Object.defineProperty(HTMLCanvasElement.prototype, 'captureStream', {
      configurable: true,
      value: vi.fn(() => placeholderStream)
    })

    const pipVideo = await createMeetingPictureInPictureVideo()

    expect(pipVideo.srcObject).toBe(placeholderStream)

    releaseMeetingPictureInPictureVideo()
    expect(placeholderTrack.stop).toHaveBeenCalledOnce()
  })
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

  it('PiP 회의가 활성화되어 있으면 다른 회의 입장을 차단한다', () => {
    const notify = vi.fn()
    meetingDocumentMode.begin({ meetingId: 12, teamId: 3, returnRoute: '/meetings/12' })

    expect(meetingDocumentMode.allowMeetingAction(notify)).toBe(false)
    expect(notify).toHaveBeenCalledWith('이미 회의에 참여 중입니다.')

    meetingDocumentMode.clear()

    expect(meetingDocumentMode.allowMeetingAction(notify)).toBe(true)
    expect(notify).toHaveBeenCalledTimes(1)
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

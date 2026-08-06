import { createApp } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const RoomEvent = {
  Connected: 'connected',
  Reconnecting: 'reconnecting',
  Reconnected: 'reconnected',
  ParticipantConnected: 'participantConnected',
  ParticipantDisconnected: 'participantDisconnected',
  TrackSubscribed: 'trackSubscribed',
  TrackUnsubscribed: 'trackUnsubscribed',
  TrackMuted: 'trackMuted',
  TrackUnmuted: 'trackUnmuted',
  TrackPublished: 'trackPublished',
  TrackUnpublished: 'trackUnpublished',
  LocalTrackPublished: 'localTrackPublished',
  LocalTrackUnpublished: 'localTrackUnpublished',
  DataReceived: 'dataReceived',
  ActiveSpeakersChanged: 'activeSpeakersChanged',
  Disconnected: 'disconnected'
}

class FakeRoom {
  constructor() {
    this.handlers = new Map()
    this.localParticipant = null
    this.remoteParticipants = new Map()
  }

  on(event, handler) {
    this.handlers.set(event, handler)
    return this
  }

  emit(event, ...args) {
    this.handlers.get(event)?.(...args)
  }

  async connect() {}

  async disconnect() {
    this.emit(RoomEvent.Disconnected, 0)
  }
}

vi.mock('livekit-client', () => ({
  Room: FakeRoom,
  RoomEvent,
  Track: {
    Kind: { Video: 'video', Audio: 'audio' },
    Source: {
      Camera: 'camera',
      Microphone: 'microphone',
      ScreenShare: 'screen_share',
      ScreenShareAudio: 'screen_share_audio'
    }
  },
  DisconnectReason: {
    PARTICIPANT_REMOVED: 4,
    ROOM_DELETED: 5,
    ROOM_CLOSED: 6,
    DUPLICATE_IDENTITY: 7
  }
}))

const { useLiveKitMeeting } = await import('./useLiveKitMeeting')

function mountComposable(options) {
  let api
  const app = createApp({
    setup() {
      api = useLiveKitMeeting(options)
      return () => null
    }
  })
  app.mount(document.createElement('div'))
  return { api, unmount: () => app.unmount() }
}

function createLocalParticipant(overrides = {}) {
  return {
    identity: 'participant-7',
    name: '지니',
    publishData: vi.fn().mockResolvedValue(undefined),
    getTrackPublication: () => undefined,
    ...overrides
  }
}

function encode(value) {
  return new TextEncoder().encode(typeof value === 'string' ? value : JSON.stringify(value))
}

async function connectRoom(options = {}) {
  const onChatMessage = vi.fn()
  const { api, unmount } = mountComposable({ onChatMessage, ...options })
  await api.connect({ url: 'ws://livekit.test', token: 'token' })
  const room = api.room.value
  room.localParticipant = createLocalParticipant()
  return { api, room, onChatMessage, unmount }
}

describe('useLiveKitMeeting 채팅', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('LiveKit connection lifecycle', () => {
    it('문서 PiP 모드에서는 화면 컴포넌트가 바뀌어도 회의 연결을 유지한다', async () => {
      const { api, room, unmount } = await connectRoom({
        preserveConnectionOnUnmount: () => true
      })
      const disconnect = vi.spyOn(room, 'disconnect')

      unmount()
      await Promise.resolve()

      expect(disconnect).not.toHaveBeenCalled()
    })

    it('reuses the active room when the same token is loaded again', async () => {
      const onDisconnected = vi.fn()
      const { api, room, unmount } = await connectRoom({ onDisconnected })

      const connectedAgain = await api.connect({ url: 'ws://livekit.test', token: 'token' })

      expect(connectedAgain).toBe(room)
      expect(api.room.value).toBe(room)
      expect(onDisconnected).not.toHaveBeenCalled()
      unmount()
    })

    it('does not treat a token refresh room replacement as meeting termination', async () => {
      const onDisconnected = vi.fn()
      const { api, room, unmount } = await connectRoom({ onDisconnected })

      await api.connect({ url: 'ws://livekit.test', token: 'refreshed-token' })

      expect(api.room.value).not.toBe(room)
      expect(onDisconnected).not.toHaveBeenCalled()
      unmount()
    })
  })

  describe('sendChatMessage', () => {
    it('chat 토픽과 reliable 옵션으로 발행한다', async () => {
      const { api, room, unmount } = await connectRoom()

      await api.sendChatMessage('회의 자료 올렸습니다')

      expect(room.localParticipant.publishData).toHaveBeenCalledTimes(1)
      const [payload, publishOptions] = room.localParticipant.publishData.mock.calls[0]
      expect(publishOptions).toEqual({ reliable: true, topic: 'chat' })
      expect(JSON.parse(new TextDecoder().decode(payload))).toMatchObject({
        v: 1,
        body: '회의 자료 올렸습니다'
      })
      unmount()
    })

    it('본인 표시용으로 발행 결과를 돌려준다', async () => {
      const { api, unmount } = await connectRoom()

      const sent = await api.sendChatMessage('안녕하세요')

      expect(sent).toMatchObject({
        body: '안녕하세요',
        sender: '지니',
        senderIdentity: 'participant-7'
      })
      expect(Number.isNaN(new Date(sent.sentAt).getTime())).toBe(false)
      unmount()
    })

    it('연결 전에는 예외를 던진다', async () => {
      const { api, unmount } = mountComposable({})

      await expect(api.sendChatMessage('전송 불가')).rejects.toThrow(
        '회의 연결이 완료되지 않았습니다.'
      )
      unmount()
    })
  })

  describe('DataReceived 수신', () => {
    it('유효한 메시지를 onChatMessage로 전달한다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()

      room.emit(
        RoomEvent.DataReceived,
        encode({ v: 1, body: '네 확인했습니다', sentAt: '2026-08-05T00:31:16.000Z' }),
        { identity: 'participant-3', name: '이지은' },
        undefined,
        'chat'
      )

      expect(onChatMessage).toHaveBeenCalledWith({
        body: '네 확인했습니다',
        sentAt: '2026-08-05T00:31:16.000Z',
        sender: '이지은',
        senderIdentity: 'participant-3'
      })
      unmount()
    })

    it('발신자 이름은 페이로드가 아니라 participant.name을 쓴다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()

      room.emit(
        RoomEvent.DataReceived,
        encode({ v: 1, body: '사칭 시도', sender: '관리자' }),
        { identity: 'participant-9', name: '박정민' },
        undefined,
        'chat'
      )

      expect(onChatMessage).toHaveBeenCalledTimes(1)
      expect(onChatMessage.mock.calls[0][0].sender).toBe('박정민')
      unmount()
    })

    it('name이 없으면 identity로 대체한다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()

      room.emit(
        RoomEvent.DataReceived,
        encode({ v: 1, body: '이름 없는 참가자' }),
        { identity: 'participant-11' },
        undefined,
        'chat'
      )

      expect(onChatMessage.mock.calls[0][0].sender).toBe('participant-11')
      unmount()
    })

    it('chat 이외의 토픽은 무시한다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()

      room.emit(
        RoomEvent.DataReceived,
        encode({ v: 1, body: '다른 기능 데이터' }),
        { identity: 'participant-3', name: '이지은' },
        undefined,
        'reaction'
      )

      expect(onChatMessage).not.toHaveBeenCalled()
      unmount()
    })

    it('JSON이 아닌 페이로드는 예외 없이 버린다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()

      expect(() =>
        room.emit(
          RoomEvent.DataReceived,
          encode('깨진 payload'),
          { identity: 'participant-3', name: '이지은' },
          undefined,
          'chat'
        )
      ).not.toThrow()
      expect(onChatMessage).not.toHaveBeenCalled()
      unmount()
    })

    it('body가 비었거나 문자열이 아니면 버린다', async () => {
      const { room, onChatMessage, unmount } = await connectRoom()
      const sender = { identity: 'participant-3', name: '이지은' }

      room.emit(RoomEvent.DataReceived, encode({ v: 1, body: '' }), sender, undefined, 'chat')
      room.emit(RoomEvent.DataReceived, encode({ v: 1, body: 42 }), sender, undefined, 'chat')
      room.emit(RoomEvent.DataReceived, encode({ v: 1 }), sender, undefined, 'chat')

      expect(onChatMessage).not.toHaveBeenCalled()
      unmount()
    })
  })
})

import { onBeforeUnmount, reactive, ref, shallowRef } from 'vue'
import {
  DisconnectReason,
  Room,
  RoomEvent,
  Track
} from 'livekit-client'

const disconnectMessage = (reason, requestedExit) => {
  if (requestedExit === 'leave') return '회의에서 나갔습니다.'
  if (requestedExit === 'end') return '회의를 종료했습니다.'
  if (reason === DisconnectReason.PARTICIPANT_REMOVED) {
    return '회의 연결이 종료되었습니다.'
  }
  if (
    reason === DisconnectReason.ROOM_DELETED ||
    reason === DisconnectReason.ROOM_CLOSED
  ) {
    return '호스트가 회의를 종료했습니다.'
  }
  if (reason === DisconnectReason.DUPLICATE_IDENTITY) {
    return '같은 계정으로 다른 창에서 회의에 입장해 연결이 종료되었습니다.'
  }
  return '회의 연결이 종료되었습니다.'
}

export function useLiveKitMeeting(options = {}) {
  const room = shallowRef(null)
  const status = ref('disconnected')
  const error = ref('')
  const participantIdentities = ref([])
  const screenShareIdentities = ref([])
  const activeSpeakerIdentities = ref([])
  const deviceState = reactive({
    microphone: false,
    camera: false,
    screen: false
  })

  const mediaByIdentity = new Map()
  const containerByMediaKey = new Map()
  let requestedExit = ''
  let disposed = false

  const syncParticipants = () => {
    const currentRoom = room.value
    if (!currentRoom) {
      participantIdentities.value = []
      return
    }
    participantIdentities.value = [
      currentRoom.localParticipant?.identity,
      ...currentRoom.remoteParticipants.keys()
    ].filter(Boolean)
  }

  const appendMedia = mediaKey => {
    const container = containerByMediaKey.get(mediaKey)
    if (!container) return
    for (const entries of mediaByIdentity.values()) {
      for (const entry of entries) {
        if (entry.mediaKey !== mediaKey) continue
        if (entry.isAudio) continue
        if (entry.element.parentElement !== container) {
          container.appendChild(entry.element)
        }
      }
    }
  }

  const syncScreenShares = () => {
    const identities = []
    for (const [identity, entries] of mediaByIdentity.entries()) {
      if (entries.some(entry => entry.isScreenShare && !entry.isAudio)) {
        identities.push(identity)
      }
    }
    screenShareIdentities.value = identities
  }

  const mountParticipantMedia = (mediaKey, element) => {
    if (!mediaKey) return
    if (!element) {
      containerByMediaKey.delete(mediaKey)
      return
    }
    containerByMediaKey.set(mediaKey, element)
    appendMedia(mediaKey)
  }

  const attachTrack = (track, publication, participant) => {
    if (!track || !participant?.identity) return
    const source = publication?.source || track.source
    const isAudio = track.kind !== Track.Kind.Video
    const isScreenShare = source === Track.Source.ScreenShare
    const mediaKey = isScreenShare
      ? `screen:${participant.identity}`
      : participant.identity
    const element = track.attach()
    element.autoplay = true
    element.playsInline = true
    element.dataset.livekitTrackSid = track.sid || ''
    const isLocal = participant === room.value?.localParticipant
    if (!isAudio) {
      element.classList.add('livekit-video')
    } else {
      element.classList.add('livekit-audio')
      if (!isLocal) document.body.appendChild(element)
    }
    if (isLocal) element.muted = true

    const entries = mediaByIdentity.get(participant.identity) || []
    entries.push({ track, element, isAudio, isScreenShare, mediaKey })
    mediaByIdentity.set(participant.identity, entries)
    appendMedia(mediaKey)
    syncScreenShares()
  }

  const detachTrack = track => {
    if (!track) return
    for (const [identity, entries] of mediaByIdentity.entries()) {
      const remaining = entries.filter(entry => {
        if (entry.track !== track) return true
        entry.track.detach(entry.element)
        entry.element.remove()
        return false
      })
      if (remaining.length) mediaByIdentity.set(identity, remaining)
      else mediaByIdentity.delete(identity)
    }
    syncScreenShares()
  }

  const clearMedia = () => {
    for (const entries of mediaByIdentity.values()) {
      for (const { track, element } of entries) {
        track.detach(element)
        element.remove()
      }
    }
    mediaByIdentity.clear()
    containerByMediaKey.clear()
    screenShareIdentities.value = []
  }

  const syncLocalDeviceState = (publication, enabled) => {
    if (publication?.source === Track.Source.Microphone) {
      deviceState.microphone = enabled
    } else if (publication?.source === Track.Source.Camera) {
      deviceState.camera = enabled
    } else if (
      publication?.source === Track.Source.ScreenShare ||
      publication?.source === Track.Source.ScreenShareAudio
    ) {
      deviceState.screen = enabled
    }
  }

  const registerRoomEvents = liveRoom => {
    liveRoom
      .on(RoomEvent.Connected, () => {
        status.value = 'connected'
        error.value = ''
        syncParticipants()
        options.onStatusChange?.('connected')
      })
      .on(RoomEvent.Reconnecting, () => {
        status.value = 'reconnecting'
        options.onStatusChange?.('reconnecting')
      })
      .on(RoomEvent.Reconnected, () => {
        status.value = 'connected'
        syncParticipants()
        options.onStatusChange?.('connected')
      })
      .on(RoomEvent.ParticipantConnected, syncParticipants)
      .on(RoomEvent.ParticipantDisconnected, syncParticipants)
      .on(RoomEvent.TrackSubscribed, (track, publication, participant) => {
        attachTrack(track, publication, participant)
      })
      .on(RoomEvent.TrackUnsubscribed, track => detachTrack(track))
      .on(RoomEvent.LocalTrackPublished, (publication, participant) => {
        syncLocalDeviceState(publication, true)
        attachTrack(publication.track, publication, participant)
      })
      .on(RoomEvent.LocalTrackUnpublished, publication => {
        syncLocalDeviceState(publication, false)
        detachTrack(publication.track)
      })
      .on(RoomEvent.ActiveSpeakersChanged, speakers => {
        activeSpeakerIdentities.value = speakers.map(speaker => speaker.identity)
      })
      .on(RoomEvent.Disconnected, reason => {
        status.value = 'disconnected'
        deviceState.microphone = false
        deviceState.camera = false
        deviceState.screen = false
        syncParticipants()
        clearMedia()
        options.onStatusChange?.('disconnected')
        if (!disposed) {
          options.onDisconnected?.({
            reason,
            requestedExit,
            message: disconnectMessage(reason, requestedExit)
          })
        }
        requestedExit = ''
      })
  }

  const connect = async connection => {
    if (!connection?.url || !connection?.token) {
      throw new Error('LiveKit 연결 정보가 없습니다.')
    }
    if (room.value) await room.value.disconnect()

    status.value = 'connecting'
    error.value = ''
    const liveRoom = new Room({ adaptiveStream: true, dynacast: true })
    room.value = liveRoom
    registerRoomEvents(liveRoom)

    try {
      await liveRoom.connect(connection.url, connection.token)
      syncParticipants()
      return liveRoom
    } catch (connectionError) {
      status.value = 'error'
      error.value = connectionError?.message || '화상회의 서버에 연결하지 못했습니다.'
      throw connectionError
    }
  }

  const setDeviceEnabled = async (device, enabled) => {
    const localParticipant = room.value?.localParticipant
    if (!localParticipant) throw new Error('회의 연결이 완료되지 않았습니다.')

    if (device === 'microphone') {
      await localParticipant.setMicrophoneEnabled(enabled)
    } else if (device === 'camera') {
      await localParticipant.setCameraEnabled(enabled)
    } else if (device === 'screen') {
      await localParticipant.setScreenShareEnabled(enabled)
    } else {
      throw new Error(`지원하지 않는 장치입니다: ${device}`)
    }
    deviceState[device] = enabled
    return enabled
  }

  const requestServerExit = action => {
    requestedExit = action
  }

  const disconnectAfterServerExit = async action => {
    requestedExit = action
    if (room.value) await room.value.disconnect()
  }

  onBeforeUnmount(async () => {
    disposed = true
    if (room.value) await room.value.disconnect()
    clearMedia()
  })

  return {
    room,
    status,
    error,
    participantIdentities,
    screenShareIdentities,
    activeSpeakerIdentities,
    deviceState,
    connect,
    mountParticipantMedia,
    setDeviceEnabled,
    requestServerExit,
    disconnectAfterServerExit
  }
}

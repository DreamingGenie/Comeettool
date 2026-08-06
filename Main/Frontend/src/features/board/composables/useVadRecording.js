import { ref } from 'vue'
import { Track } from 'livekit-client'

const MAX_UPLOAD_RETRIES = 5
const SPEECH_THRESHOLD = 0.025
const SILENCE_MS = 700
const MIN_SPEECH_MS = 400
const POLL_MS = 100

function getSupportedMimeType() {
  for (const type of [
    'audio/ogg;codecs=opus',
    'audio/webm;codecs=opus',
    'audio/webm'
  ]) {
    if (MediaRecorder.isTypeSupported(type)) return type
  }
  return ''
}

function computeRms(data) {
  let sum = 0
  for (let index = 0; index < data.length; index += 1) {
    const normalized = (data[index] - 128) / 128
    sum += normalized * normalized
  }
  return Math.sqrt(sum / data.length)
}

/**
 * MEET-12 임시 VAD 업로드 PoC.
 * LiveKit 로컬 마이크 트랙을 간단한 음량 임계값으로 구간 절단 후 직렬 업로드한다.
 */
export function useVadRecording(options = {}) {
  const status = ref('idle')
  const pendingUploads = ref(0)

  let meetingId = null
  let sequence = 1
  let uploadChain = Promise.resolve()
  let running = false
  let pollTimer = 0
  let audioContext = null
  let analyser = null
  let sourceNode = null
  let mediaRecorder = null
  let chunkStartedAt = 0
  let recordedChunks = []
  let isSpeaking = false
  let silenceStartedAt = 0
  let mediaStreamTrack = null
  let recorderStopPromise = Promise.resolve()

  async function uploadChunk(blob, startedAt, endedAt, attemptSequence) {
    if (typeof options.uploadRecording !== 'function') {
      throw new Error('VAD 업로드 함수가 설정되지 않았습니다.')
    }

    let currentSequence = attemptSequence

    for (let attempt = 0; attempt < MAX_UPLOAD_RETRIES; attempt += 1) {
      try {
        await options.uploadRecording(meetingId, {
          sequence: currentSequence,
          startedAt,
          endedAt,
          audio: blob
        })
        sequence = currentSequence + 1
        options.onUploadSuccess?.({
          meetingId,
          sequence: currentSequence,
          startedAt,
          endedAt
        })
        return
      } catch (error) {
        const expectedSequence = error?.payload?.data?.expectedSequence
        if (error?.status === 409 && expectedSequence != null) {
          currentSequence = expectedSequence
          continue
        }
        options.onUploadError?.(error)
        throw error
      }
    }

    throw new Error('VAD 업로드 재시도 한도를 초과했습니다.')
  }

  function enqueueUpload(task) {
    pendingUploads.value += 1
    uploadChain = uploadChain
      .then(task)
      .catch(error => {
        console.error('[VAD upload failed]', error)
      })
      .finally(() => {
        pendingUploads.value = Math.max(0, pendingUploads.value - 1)
      })
    return uploadChain
  }

  function startRecorder() {
    if (!mediaStreamTrack || mediaRecorder?.state === 'recording') return

    const mimeType = getSupportedMimeType()
    if (!mimeType) {
      throw new Error('브라우저가 오디오 녹음을 지원하지 않습니다.')
    }

    recordedChunks = []
    chunkStartedAt = Date.now()
    mediaRecorder = new MediaRecorder(new MediaStream([mediaStreamTrack]), {
      mimeType
    })
    mediaRecorder.ondataavailable = event => {
      if (event.data?.size) recordedChunks.push(event.data)
    }
    mediaRecorder.start()
  }

  function finishRecorder() {
    if (!mediaRecorder || mediaRecorder.state === 'inactive') return recorderStopPromise

    const startedAt = chunkStartedAt
    const recorder = mediaRecorder

    recorderStopPromise = new Promise(resolve => {
      recorder.onstop = () => {
        mediaRecorder = null
        const endedAt = Date.now()
        if (endedAt - startedAt < MIN_SPEECH_MS) {
          resolve()
          return
        }

        const blob = new Blob(recordedChunks, { type: recorder.mimeType })
        if (!blob.size) {
          resolve()
          return
        }

        const uploadSequence = sequence
        resolve(enqueueUpload(() => uploadChunk(blob, startedAt, endedAt, uploadSequence)))
      }
    })

    recorder.stop()
    return recorderStopPromise
  }

  function pollSpeech() {
    if (!running || !analyser) return

    const data = new Uint8Array(analyser.fftSize)
    analyser.getByteTimeDomainData(data)
    const rms = computeRms(data)
    const now = Date.now()

    if (rms >= SPEECH_THRESHOLD) {
      silenceStartedAt = 0
      if (!isSpeaking) {
        isSpeaking = true
        try {
          startRecorder()
        } catch (error) {
          options.onUploadError?.(error)
          stopMonitoring()
          return
        }
      }
    } else if (isSpeaking) {
      if (!silenceStartedAt) silenceStartedAt = now
      if (now - silenceStartedAt >= SILENCE_MS) {
        isSpeaking = false
        silenceStartedAt = 0
        finishRecorder()
      }
    }

    pollTimer = window.setTimeout(pollSpeech, POLL_MS)
  }

  function resolveMicTrack(room) {
    const publication = room?.localParticipant?.getTrackPublication(
      Track.Source.Microphone
    )
    return publication?.track?.mediaStreamTrack || null
  }

  function start(id, room) {
    stopMonitoring()

    meetingId = id
    mediaStreamTrack = resolveMicTrack(room)
    if (!mediaStreamTrack) return false

    audioContext = new AudioContext()
    sourceNode = audioContext.createMediaStreamSource(
      new MediaStream([mediaStreamTrack])
    )
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 2048
    sourceNode.connect(analyser)

    running = true
    status.value = 'recording'
    pollSpeech()
    return true
  }

  function stopMonitoring() {
    running = false

    if (pollTimer) {
      window.clearTimeout(pollTimer)
      pollTimer = 0
    }

    if (isSpeaking) {
      isSpeaking = false
      finishRecorder()
    } else if (mediaRecorder?.state === 'recording') {
      finishRecorder()
    }

    sourceNode?.disconnect()
    sourceNode = null
    analyser = null
    mediaStreamTrack = null

    if (audioContext) {
      audioContext.close().catch(() => undefined)
      audioContext = null
    }

    status.value = 'idle'
    return recorderStopPromise
  }

  async function stopAndDrain() {
    await stopMonitoring()
    await uploadChain
  }

  return {
    status,
    pendingUploads,
    start,
    stopMonitoring,
    stopAndDrain,
    resolveMicTrack
  }
}

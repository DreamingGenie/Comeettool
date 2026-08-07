import { ref } from 'vue'
import { Track } from 'livekit-client'
import { MicVAD } from '@ricky0123/vad-web'

const MAX_UPLOAD_RETRIES = 5
const VAD_PACKAGE_VERSION = '0.0.30'
const ONNX_RUNTIME_VERSION = '1.27.0'

export const SILERO_VAD_CONFIG = Object.freeze({
  model: 'v5',
  positiveSpeechThreshold: 0.5,
  negativeSpeechThreshold: 0.35,
  minSpeechMs: 250,
  redemptionMs: 800,
  preSpeechPadMs: 200,
  maxChunkMs: 30_000
})

const VAD_ASSET_BASE_PATH =
  `https://cdn.jsdelivr.net/npm/@ricky0123/vad-web@${VAD_PACKAGE_VERSION}/dist/`
const ONNX_WASM_BASE_PATH =
  `https://cdn.jsdelivr.net/npm/onnxruntime-web@${ONNX_RUNTIME_VERSION}/dist/`

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

function stopStream(stream) {
  stream?.getTracks().forEach(track => track.stop())
}

/**
 * LiveKit의 로컬 마이크 트랙은 그대로 유지하면서 Silero VAD로 발화 구간만 판정한다.
 * 실제 업로드 파일은 기존 MEET-12 규격을 지키기 위해 MediaRecorder로 생성한다.
 */
export function useVadRecording(options = {}) {
  const status = ref('idle')
  const pendingUploads = ref(0)

  let meetingId = null
  let sequence = 1
  let uploadChain = Promise.resolve()
  let running = false
  let lifecycle = 0
  let vad = null
  let vadStream = null
  let mediaStreamTrack = null
  let recorderAudioContext = null
  let recorderSourceNode = null
  let recorderDelayNode = null
  let recorderDestinationNode = null
  let mediaRecorder = null
  let chunkStartedAt = 0
  let chunkTimer = 0
  let recordedChunks = []
  let isSpeaking = false
  let isValidatedSpeech = false
  let recorderStopPromise = Promise.resolve()

  const createVad = options.createVad || (vadOptions => MicVAD.new(vadOptions))

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

    throw new Error('VAD 업로드 재시도 횟수를 초과했습니다.')
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

  function clearChunkTimer() {
    if (!chunkTimer) return
    window.clearTimeout(chunkTimer)
    chunkTimer = 0
  }

  function scheduleMaximumChunkSplit() {
    clearChunkTimer()
    chunkTimer = window.setTimeout(async () => {
      await finishRecorder({ upload: isValidatedSpeech })
      if (running && isSpeaking) startRecorder()
    }, SILERO_VAD_CONFIG.maxChunkMs)
  }

  function startRecorder() {
    if (!recorderDestinationNode || mediaRecorder?.state === 'recording') return

    const mimeType = getSupportedMimeType()
    if (!mimeType) {
      throw new Error('브라우저가 오디오 녹음을 지원하지 않습니다.')
    }

    recordedChunks = []
    chunkStartedAt = Date.now() - SILERO_VAD_CONFIG.preSpeechPadMs
    mediaRecorder = new MediaRecorder(recorderDestinationNode.stream, {
      mimeType
    })
    mediaRecorder.ondataavailable = event => {
      if (event.data?.size) recordedChunks.push(event.data)
    }
    mediaRecorder.start()
    scheduleMaximumChunkSplit()
  }

  function finishRecorder({ upload = true } = {}) {
    clearChunkTimer()
    if (!mediaRecorder || mediaRecorder.state === 'inactive') return recorderStopPromise

    const startedAt = chunkStartedAt
    const recorder = mediaRecorder

    recorderStopPromise = new Promise(resolve => {
      recorder.onstop = () => {
        if (mediaRecorder === recorder) mediaRecorder = null
        const endedAt = Date.now()
        const chunks = recordedChunks
        recordedChunks = []

        if (!upload || endedAt - startedAt < SILERO_VAD_CONFIG.minSpeechMs) {
          resolve()
          return
        }

        const blob = new Blob(chunks, { type: recorder.mimeType })
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

  function resolveMicTrack(room) {
    const publication = room?.localParticipant?.getTrackPublication(
      Track.Source.Microphone
    )
    return publication?.track?.mediaStreamTrack || null
  }

  async function createRecorderPipeline(track) {
    recorderAudioContext = new AudioContext()
    if (recorderAudioContext.state === 'suspended') {
      await recorderAudioContext.resume()
    }
    recorderSourceNode = recorderAudioContext.createMediaStreamSource(
      new MediaStream([track])
    )
    recorderDelayNode = recorderAudioContext.createDelay(
      SILERO_VAD_CONFIG.preSpeechPadMs / 1000
    )
    recorderDelayNode.delayTime.value = SILERO_VAD_CONFIG.preSpeechPadMs / 1000
    recorderDestinationNode = recorderAudioContext.createMediaStreamDestination()
    recorderSourceNode.connect(recorderDelayNode)
    recorderDelayNode.connect(recorderDestinationNode)
  }

  async function start(id, room) {
    const currentLifecycle = ++lifecycle
    await stopMonitoring({ preserveLifecycle: true })

    meetingId = id
    mediaStreamTrack = resolveMicTrack(room)
    if (!mediaStreamTrack) return false

    status.value = 'initializing'
    try {
      await createRecorderPipeline(mediaStreamTrack)
      vadStream = new MediaStream([mediaStreamTrack.clone()])
      const createdVad = await createVad({
        ...SILERO_VAD_CONFIG,
        startOnLoad: false,
        baseAssetPath: VAD_ASSET_BASE_PATH,
        onnxWASMBasePath: ONNX_WASM_BASE_PATH,
        getStream: async () => vadStream,
        pauseStream: async stream => stopStream(stream),
        resumeStream: async () => {
          vadStream = new MediaStream([mediaStreamTrack.clone()])
          return vadStream
        },
        onSpeechStart: () => {
          if (!running) return
          isSpeaking = true
          isValidatedSpeech = false
          try {
            startRecorder()
          } catch (error) {
            options.onUploadError?.(error)
            void stopMonitoring()
          }
        },
        onSpeechRealStart: () => {
          if (running) isValidatedSpeech = true
        },
        onVADMisfire: () => {
          isSpeaking = false
          isValidatedSpeech = false
          void finishRecorder({ upload: false })
        },
        onSpeechEnd: () => {
          const shouldUpload = isValidatedSpeech
          isSpeaking = false
          isValidatedSpeech = false
          void finishRecorder({ upload: shouldUpload })
        }
      })

      if (currentLifecycle !== lifecycle || !mediaStreamTrack) {
        await createdVad.destroy()
        return false
      }

      vad = createdVad
      running = true
      await createdVad.start()
      status.value = 'recording'
      return true
    } catch (error) {
      options.onUploadError?.(
        new Error(`Silero VAD 초기화에 실패했습니다: ${error?.message || error}`)
      )
      await stopMonitoring()
      return false
    }
  }

  async function stopMonitoring({ preserveLifecycle = false } = {}) {
    if (!preserveLifecycle) lifecycle += 1
    running = false
    clearChunkTimer()

    const shouldUpload = isSpeaking && isValidatedSpeech
    isSpeaking = false
    isValidatedSpeech = false
    await finishRecorder({ upload: shouldUpload })

    const currentVad = vad
    vad = null
    if (currentVad) {
      try {
        await currentVad.destroy()
      } catch (error) {
        console.warn('[VAD destroy failed]', error)
      }
    }

    stopStream(vadStream)
    vadStream = null
    recorderSourceNode?.disconnect()
    recorderDelayNode?.disconnect()
    recorderSourceNode = null
    recorderDelayNode = null
    recorderDestinationNode = null
    mediaStreamTrack = null

    if (recorderAudioContext) {
      await recorderAudioContext.close().catch(() => undefined)
      recorderAudioContext = null
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

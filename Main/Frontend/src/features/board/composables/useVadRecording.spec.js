import { describe, expect, it } from 'vitest'
import { SILERO_VAD_CONFIG } from './useVadRecording'

describe('Silero VAD 설정', () => {
  it('프로토타입 발화 판정 기준을 사용한다', () => {
    expect(SILERO_VAD_CONFIG).toEqual({
      model: 'v5',
      positiveSpeechThreshold: 0.5,
      negativeSpeechThreshold: 0.35,
      minSpeechMs: 250,
      redemptionMs: 800,
      preSpeechPadMs: 200,
      maxChunkMs: 30_000
    })
  })

  it('종료 임계값은 시작 임계값보다 낮아 짧은 확률 변동을 흡수한다', () => {
    expect(SILERO_VAD_CONFIG.negativeSpeechThreshold).toBeLessThan(
      SILERO_VAD_CONFIG.positiveSpeechThreshold
    )
  })
})

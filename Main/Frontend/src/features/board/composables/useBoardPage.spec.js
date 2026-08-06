import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, nextTick, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { boardStore } from '../stores/boardStore'
import { useBoardPage } from './useBoardPage'

vi.mock('vue-router', () => ({
  useRoute: vi.fn()
}))

const flushWatchers = async () => {
  await nextTick()
  await Promise.resolve()
}

describe('useBoardPage meeting route lifecycle', () => {
  let app
  let host
  let route
  let loadResources

  const mountPage = resources => {
    app = createApp(
      defineComponent({
        setup() {
          useBoardPage({ resources })
          return () => null
        }
      })
    )
    host = document.createElement('div')
    document.body.appendChild(host)
    app.mount(host)
  }

  beforeEach(() => {
    boardStore.reset()
    route = reactive({ params: { meetingId: '42' } })
    useRoute.mockReturnValue(route)
    loadResources = vi
      .spyOn(boardStore, 'loadResources')
      .mockResolvedValue(undefined)
  })

  afterEach(() => {
    app?.unmount()
    host?.remove()
    vi.restoreAllMocks()
  })

  it('회의에서 문서 라우트로 이동해도 meetingRoom을 다시 로드하지 않는다', async () => {
    boardStore.state.currentMeetingId = '42'
    mountPage(['meetingRoom'])
    await flushWatchers()

    expect(loadResources).toHaveBeenCalledTimes(1)
    expect(loadResources).toHaveBeenLastCalledWith(
      ['meetingRoom'],
      expect.objectContaining({ meetingId: '42' })
    )

    route.params = { teamId: '7' }
    await flushWatchers()

    expect(loadResources).toHaveBeenCalledTimes(1)
  })

  it('문서 페이지 사이를 이동해도 유지 중인 회의에 중복 참가하지 않는다', async () => {
    boardStore.state.currentMeetingId = '42'
    mountPage(['meetingRoom'])
    await flushWatchers()

    route.params = { teamId: '7' }
    await flushWatchers()
    route.params = { teamId: '8' }
    await flushWatchers()

    expect(loadResources).toHaveBeenCalledTimes(1)
  })

  it('다른 회의 라우트로 직접 이동한 경우에만 meetingRoom을 다시 로드한다', async () => {
    boardStore.state.currentMeetingId = '42'
    mountPage(['meetingRoom'])
    await flushWatchers()

    route.params = { meetingId: '43' }
    await flushWatchers()

    expect(loadResources).toHaveBeenCalledTimes(2)
    expect(loadResources).toHaveBeenLastCalledWith(
      ['meetingRoom'],
      expect.objectContaining({ meetingId: '43' })
    )
  })
})

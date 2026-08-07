import { describe, expect, it } from 'vitest'

import {
  OTHERS_PER_PAGE_PINNED,
  PARTICIPANTS_PER_PAGE,
  clampPage,
  computeVideoLayoutClass,
  computeVideoPageCount,
  computeVisibleTiles,
  nextLayoutMode,
  normalizeLayoutMode
} from './videoLayout'

const tiles = count =>
  Array.from({ length: count }, (_, index) => ({ id: `t${index}` }))

const ids = list => list.map(tile => tile.id)

describe('videoLayout', () => {
  describe('normalizeLayoutMode (#1 레이아웃 옵션)', () => {
    it("'grid'만 grid로 인정한다", () => {
      expect(normalizeLayoutMode('grid')).toBe('grid')
    })

    it("'paged'는 그대로 paged", () => {
      expect(normalizeLayoutMode('paged')).toBe('paged')
    })

    it('null·undefined·알 수 없는 값은 paged로 정규화한다', () => {
      expect(normalizeLayoutMode(null)).toBe('paged')
      expect(normalizeLayoutMode(undefined)).toBe('paged')
      expect(normalizeLayoutMode('list')).toBe('paged')
    })
  })

  describe('nextLayoutMode (#1 토글)', () => {
    it('grid <-> paged 를 오간다', () => {
      expect(nextLayoutMode('grid')).toBe('paged')
      expect(nextLayoutMode('paged')).toBe('grid')
    })
  })

  describe('computeVideoPageCount', () => {
    it('grid 모드는 참가자 수와 무관하게 항상 1페이지 (#1)', () => {
      expect(
        computeVideoPageCount({ tileCount: 20, mode: 'grid', hasPinned: false })
      ).toBe(1)
      expect(
        computeVideoPageCount({ tileCount: 20, mode: 'grid', hasPinned: true })
      ).toBe(1)
    })

    it('paged·비고정: ceil(count / 4)', () => {
      expect(
        computeVideoPageCount({ tileCount: 4, mode: 'paged', hasPinned: false })
      ).toBe(1)
      expect(
        computeVideoPageCount({ tileCount: 5, mode: 'paged', hasPinned: false })
      ).toBe(2)
      expect(
        computeVideoPageCount({ tileCount: 9, mode: 'paged', hasPinned: false })
      ).toBe(3)
    })

    it('paged·고정: 고정 타일 제외한 나머지로 계산 ceil((count-1) / 3) (#4)', () => {
      // 고정 1 + 나머지 3 = 4명 → (4-1)/3 = 1페이지
      expect(
        computeVideoPageCount({ tileCount: 4, mode: 'paged', hasPinned: true })
      ).toBe(1)
      // 고정 1 + 나머지 4 = 5명 → ceil(4/3) = 2페이지
      expect(
        computeVideoPageCount({ tileCount: 5, mode: 'paged', hasPinned: true })
      ).toBe(2)
    })

    it('참가자가 0~1명이어도 최소 1페이지', () => {
      expect(
        computeVideoPageCount({ tileCount: 0, mode: 'paged', hasPinned: false })
      ).toBe(1)
      expect(
        computeVideoPageCount({ tileCount: 1, mode: 'paged', hasPinned: true })
      ).toBe(1)
    })
  })

  describe('computeVisibleTiles', () => {
    it('grid 모드는 페이지네이션 없이 전체를 반환한다 (#1)', () => {
      const source = tiles(10)
      expect(
        computeVisibleTiles({ tiles: source, pinnedId: '', page: 0, mode: 'grid' })
      ).toHaveLength(10)
    })

    it('paged·비고정: 페이지 단위(4개)로 슬라이스', () => {
      const source = tiles(6)
      expect(
        ids(computeVisibleTiles({ tiles: source, pinnedId: '', page: 0, mode: 'paged' }))
      ).toEqual(['t0', 't1', 't2', 't3'])
      expect(
        ids(computeVisibleTiles({ tiles: source, pinnedId: '', page: 1, mode: 'paged' }))
      ).toEqual(['t4', 't5'])
    })

    it('고정 타일은 페이지가 넘어가도 항상 첫 칸에 표시된다 (#4 핵심)', () => {
      // 고정 t0 + 나머지 t1~t5
      const source = tiles(6)
      const page0 = computeVisibleTiles({ tiles: source, pinnedId: 't0', page: 0, mode: 'paged' })
      const page1 = computeVisibleTiles({ tiles: source, pinnedId: 't0', page: 1, mode: 'paged' })

      expect(ids(page0)).toEqual(['t0', 't1', 't2', 't3'])
      // 2페이지로 넘어가도 고정(t0)이 사라지지 않고 여전히 첫 칸
      expect(page1[0].id).toBe('t0')
      expect(ids(page1)).toEqual(['t0', 't4', 't5'])
    })

    it('고정 id가 목록에 없으면 나머지만 반환한다(빈 슬롯 없음)', () => {
      const source = tiles(4)
      const result = computeVisibleTiles({ tiles: source, pinnedId: 'ghost', page: 0, mode: 'paged' })
      expect(result.every(Boolean)).toBe(true)
      expect(ids(result)).toEqual(['t0', 't1', 't2'])
    })

    it('빈 목록·비배열도 안전하게 빈 배열을 반환한다', () => {
      expect(computeVisibleTiles({ tiles: [], pinnedId: '', page: 0, mode: 'paged' })).toEqual([])
      expect(computeVisibleTiles({ tiles: null, pinnedId: '', page: 0, mode: 'paged' })).toEqual([])
    })
  })

  describe('computeVideoLayoutClass', () => {
    it('grid 모드는 layout-grid (#1)', () => {
      expect(
        computeVideoLayoutClass({ visibleCount: 8, mode: 'grid', hasPinned: false })
      ).toBe('layout-grid')
    })

    it('고정 + 2명 이상은 layout-pinned (스포트라이트) (#4)', () => {
      expect(
        computeVideoLayoutClass({ visibleCount: 3, mode: 'paged', hasPinned: true })
      ).toBe('layout-pinned')
    })

    it('보이는 인원 수에 따라 single/two/three/four', () => {
      const base = { mode: 'paged', hasPinned: false }
      expect(computeVideoLayoutClass({ ...base, visibleCount: 0 })).toBe('layout-single')
      expect(computeVideoLayoutClass({ ...base, visibleCount: 1 })).toBe('layout-single')
      expect(computeVideoLayoutClass({ ...base, visibleCount: 2 })).toBe('layout-two')
      expect(computeVideoLayoutClass({ ...base, visibleCount: 3 })).toBe('layout-three')
      expect(computeVideoLayoutClass({ ...base, visibleCount: 4 })).toBe('layout-four')
      expect(computeVideoLayoutClass({ ...base, visibleCount: 9 })).toBe('layout-four')
    })
  })

  describe('clampPage (#4 참가자 퇴장 시 페이지 보정)', () => {
    it('범위 내 페이지는 그대로 둔다', () => {
      expect(clampPage(1, 3)).toBe(1)
    })

    it('페이지 수가 줄면 마지막 페이지로 당긴다', () => {
      expect(clampPage(4, 2)).toBe(1)
    })

    it('음수·0페이지 경계도 0으로 보정', () => {
      expect(clampPage(-1, 3)).toBe(0)
      expect(clampPage(2, 0)).toBe(0)
    })
  })

  describe('상수', () => {
    it('페이지당 인원/고정 스트립 칸 수 기본값', () => {
      expect(PARTICIPANTS_PER_PAGE).toBe(4)
      expect(OTHERS_PER_PAGE_PINNED).toBe(3)
    })
  })
})

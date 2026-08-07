// 회의 영상 그리드/페이지네이션 레이아웃 계산.
// MeetingView.vue의 computed에서 분리하여 순수 함수로 단위 테스트가 가능하도록 한다.

export const VIDEO_LAYOUT_STORAGE_KEY = 'comeet-video-layout'
export const PARTICIPANTS_PER_PAGE = 4
export const OTHERS_PER_PAGE_PINNED = 3 // 고정(스포트라이트) 시 우측 스트립 칸 수

// 'grid'만 명시적으로 인정하고 그 외 값(null·오타 등)은 'paged'로 정규화한다.
export const normalizeLayoutMode = value => (value === 'grid' ? 'grid' : 'paged')

// paged <-> grid 토글 시 다음 모드
export const nextLayoutMode = mode => (mode === 'grid' ? 'paged' : 'grid')

// 현재 모드/고정 여부에 따른 총 페이지 수. grid는 항상 1페이지.
// 고정 시에는 고정 타일이 페이지와 무관하게 항상 표시되므로 나머지(count-1)만 페이지 계산.
export const computeVideoPageCount = ({
  tileCount,
  mode,
  hasPinned,
  perPage = PARTICIPANTS_PER_PAGE,
  othersPerPage = OTHERS_PER_PAGE_PINNED
}) => {
  if (mode === 'grid') return 1
  if (hasPinned) {
    return Math.max(1, Math.ceil((tileCount - 1) / othersPerPage))
  }
  return Math.max(1, Math.ceil(tileCount / perPage))
}

// 현재 페이지에서 보여줄 타일 목록.
// - grid: 페이지네이션 없이 전체
// - 고정(스포트라이트): 고정 타일은 페이지와 무관하게 항상 첫 칸, 나머지만 페이지네이션
// - 일반: 페이지 단위 슬라이스
export const computeVisibleTiles = ({
  tiles,
  pinnedId,
  page,
  mode,
  perPage = PARTICIPANTS_PER_PAGE,
  othersPerPage = OTHERS_PER_PAGE_PINNED
}) => {
  const source = Array.isArray(tiles) ? tiles : []
  if (mode === 'grid') return source
  if (pinnedId) {
    const pinned = source.find(tile => tile.id === pinnedId)
    const others = source.filter(tile => tile.id !== pinnedId)
    const start = page * othersPerPage
    return [pinned, ...others.slice(start, start + othersPerPage)].filter(Boolean)
  }
  const start = page * perPage
  return source.slice(start, start + perPage)
}

// 보이는 타일 수 + 고정 여부에 따른 CSS 레이아웃 클래스
export const computeVideoLayoutClass = ({ visibleCount, mode, hasPinned }) => {
  if (mode === 'grid') return 'layout-grid'
  if (hasPinned && visibleCount > 1) return 'layout-pinned'
  if (visibleCount <= 1) return 'layout-single'
  if (visibleCount === 2) return 'layout-two'
  if (visibleCount === 3) return 'layout-three'
  return 'layout-four'
}

// 페이지 수가 줄어들 때(참가자 퇴장 등) 현재 페이지를 [0, pageCount-1] 범위로 보정
export const clampPage = (page, pageCount) =>
  Math.min(Math.max(page, 0), Math.max(0, pageCount - 1))

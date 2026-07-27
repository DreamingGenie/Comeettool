export function createDashboardTemplates({
  getWorkspaces,
  activeMeeting,
  calendarData,
  topbar,
  renderCalendar
}) {
  const homeView = () => {
    const workspaces = getWorkspaces()
    const calendar = renderCalendar(calendarData).replace('팀 일정', '나의 일정')

    return `<main class="app-shell main-home">${topbar()}<div class="main-home-layout"><aside class="workspace-sidebar"><label class="workspace-search">⌕<input placeholder="팀 스페이스 검색"></label><small>MY WORKSPACES</small><nav>${workspaces.map(workspace => `<button class="workspace-item" data-view="schedule" data-team-id="${workspace.id}"><i>${workspace.badge}</i><span><b>${workspace.name}</b><small>${workspace.role} · 멤버 ${workspace.members}명</small></span><em>•••</em></button>`).join('')}<button class="workspace-add" data-action="new-team">＋</button></nav><footer><button>ⓘ　도움말 및 지원</button><button data-action="logout">⇥　로그아웃</button></footer></aside><section class="home-dashboard"><div class="live-label"><i></i><b>회의 바로가기</b><small>1개의 회의가 진행 중입니다.</small></div><article class="live-card home-live-card"><header><div><h2>${activeMeeting.title} <small>${activeMeeting.status}</small></h2><p>${activeMeeting.description}</p></div><div class="meeting-avatars">${activeMeeting.avatars.map(avatar => `<i>${avatar}</i>`).join('')}</div></header><footer>◷ ${activeMeeting.date} · ${activeMeeting.time}　 ♙ ${activeMeeting.participantCount}명 참여 중 <button data-view="meeting">회의 입장 →</button></footer></article>${calendar}<button class="floating-add" data-action="new-meeting">＋</button></section></div></main>`
  }

  return { home: homeView }
}

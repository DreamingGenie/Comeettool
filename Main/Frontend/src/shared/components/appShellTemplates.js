export const renderLogo = () =>
  '<a class="logo" href="/" data-view="home" aria-label="코밋툴 홈"><span class="logo-mark"></span></a>'

export const renderSideIcon = name =>
  `<img class="side-icon side-icon-${name}" src="/assets/icons/${name}.svg" alt="" aria-hidden="true">`

const teamMenu = [
  ['schedule', 'calendar', '일정'],
  ['documents', 'shared', '공유 문서'],
  ['minutes', 'minutes', '회의록'],
  ['summary', 'summary', 'AI 요약'],
  ['feedback', 'star', 'AI 피드백'],
  ['members', 'members', '멤버'],
  ['settings', 'settings', '설정']
]

export function createAppShellTemplates({ team, getCurrentUser }) {
  const topbar = () => {
    const user = getCurrentUser()
    return `<header class="topbar">${renderLogo()}<button class="user-pill" data-view="profile"><i>${user.avatarText}</i> ${user.nickname}</button></header>`
  }

  const teamDock = () =>
    '<aside class="team-dock"><strong>TEAM</strong><nav><button class="team-bubble active">A7</button><button class="team-bubble">FE</button><button class="team-bubble">PL</button><button class="team-bubble add" data-action="new-team">＋</button></nav><div class="dock-bottom"><button>?</button><button data-view="profile">⚙</button></div></aside>'

  const sideNav = (active = 'schedule') =>
    `<aside class="side-nav"><small class="eyebrow">${team.eyebrow}</small><h2>${team.name}</h2><p>${team.role} · 멤버 ${team.memberCount}명</p><nav>${teamMenu
      .map(
        ([section, icon, label]) =>
          `<button class="${active === section ? 'active' : ''}" data-team-section="${section}">${renderSideIcon(icon)}<span>${label}</span>${section === 'members' ? `<b>${team.memberCount}</b>` : ''}</button>`
      )
      .join('')}</nav><button class="back-main" data-view="home">← 메인으로</button></aside>`

  const shell = (content, active = 'schedule') =>
    `<main class="app-shell">${topbar()}<div class="workspace">${teamDock()}${sideNav(active)}<section class="main-area">${content}</section></div></main>`

  const settingsSide = active =>
    `<aside class="side-nav settings-nav"><div class="search-dark">⌕　기능 검색</div><small class="settings-label">설정</small><nav class="settings-menu"><button class="${active === 'profile' ? 'active' : ''}" data-view="profile">${renderSideIcon('members')}<span>프로필 변경</span></button><button class="${active === 'password' ? 'active' : ''}" data-view="password">${renderSideIcon('lock')}<span>비밀번호 변경</span></button></nav><button class="back-main" data-view="home">← 이전으로 가기</button></aside>`

  const settingsShell = (content, active) =>
    `<main class="app-shell">${topbar()}<div class="workspace settings-shell">${settingsSide(active)}<section class="settings-content">${content}</section></div></main>`

  return { topbar, shell, settingsShell }
}

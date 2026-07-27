const archiveNames = {
  documents: '공유 문서',
  minutes: '회의록',
  summary: 'AI 요약',
  feedback: 'AI 피드백'
}

const archiveDescriptions = {
  documents: '팀원과 함께 작성 중인 문서를 확인하고 편집하세요.',
  minutes: '회의별 발언과 결정사항이 정리된 회의록입니다.',
  summary: 'AI가 회의의 핵심 내용과 액션 아이템을 정리했습니다.',
  feedback: 'AI가 분석한 회의 품질과 개선 제안을 확인하세요.'
}

export function createTeamTemplates({
  team,
  activeMeeting,
  calendarData,
  getMembers,
  getArchives,
  shell,
  renderCalendar
}) {
  const scheduleView = () =>
    shell(
      `<header class="page-heading"><div><span class="eyebrow">TEAM SCHEDULE</span><h1>${team.name} 팀 스페이스</h1><p>진행 중인 회의와 팀 일정을 한눈에 확인하세요.</p></div><button class="outline-btn" data-action="invite">초대 링크 공유</button></header><div class="live-label"><i></i><b>진행 중인 회의</b><small>1개의 회의가 진행 중입니다.</small></div><div class="meeting-cards"><article class="live-card"><h2>${activeMeeting.title}　<small style="color:#15a866">${activeMeeting.status}</small></h2><p>${activeMeeting.description}</p><footer>◷ ${activeMeeting.date} · ${activeMeeting.time}　 ♙ ${activeMeeting.participantCount}명 참여 중 <button data-view="meeting">회의 입장 →</button></footer></article><button class="create-card" data-action="new-meeting"><i>＋</i><b>새 회의 만들기</b><span>팀원들과 바로 회의를 시작하세요.</span></button></div>${renderCalendar(calendarData)}`,
      'schedule'
    )

  const membersView = () => {
    const members = getMembers()
    return shell(
      `<header class="page-heading"><div><span class="eyebrow">TEAM MEMBER</span><h1>팀 멤버</h1><p>팀 멤버를 초대하고 권한을 관리하세요</p></div><button class="primary" data-action="invite">＋ 멤버 초대</button></header><section class="member-table"><div class="table-tools"><span><button>☰ 조건</button> <button>☰ 정렬</button></span><input placeholder="⌕　 멤버를 찾아보세요..."></div><div class="member-row head"><span>이름</span><span>역할</span><span>상태</span><span>최근 활동</span></div>${members.map(member => `<article class="member-row"><div class="person"><i class="avatar">${member[0]}</i><span>${member[1]}<small>${member[2]}</small></span></div><span class="role">${member[3]}</span><span class="status ${member[4] === 'Away' ? 'away' : member[4] === 'Offline' ? 'off' : ''}">${member[4]}</span><span>${member[5]}</span></article>`).join('')}<footer class="table-foot">Showing ${members.length} of ${team.memberCount} members</footer></section>`,
      'members'
    )
  }

  const documentsView = (section = 'documents') => {
    const rows = getArchives()[section] || []
    const name = archiveNames[section]
    const icon = section === 'summary' ? 'AI' : section === 'feedback' ? '✦' : '▤'

    return shell(
      `<header class="page-heading archive-heading"><div><span class="eyebrow">MEETING ARCHIVE</span><h1>${name}</h1><p>${archiveDescriptions[section]}</p></div><button class="primary" data-action="new-document">＋ 새 문서</button></header><section class="archive-stats"><article><span>전체 문서</span><b>${rows.length}</b><small>이번 주 +${section === 'documents' ? 3 : 2}</small></article><article><span>${section === 'feedback' ? '평균 참여도' : '최근 업데이트'}</span><b>${section === 'feedback' ? '87%' : '오늘'}</b><small>${section === 'feedback' ? '지난주 대비 +4%' : '6개 변경됨'}</small></article><article><span>${section === 'summary' ? '완료된 액션' : '팀 공유'}</span><b>${section === 'summary' ? '14' : '8명'}</b><small>${section === 'summary' ? '진행 중 7개' : '모든 멤버에게 공개'}</small></article></section><section class="archive-panel"><div class="archive-tools"><div class="filter-tabs"><button class="active">전체</button><button>최근 열어본</button><button>내 문서</button></div><label>⌕<input placeholder="${name} 검색"></label><select><option>최근 수정 순</option><option>이름 순</option></select></div><div class="archive-list">${rows.map(row => `<article class="archive-row"><i class="archive-icon ${section}">${icon}</i><div class="archive-title"><b>${row[0]}</b><span>${row[1]}</span></div><div><small>${section === 'documents' ? '편집자' : '회의 정보'}</small><b>${row[2]}</b></div><div><small>업데이트</small><b>${row[3]}</b></div><em>${row[4]}</em><button data-action="open-document">열기 →</button></article>`).join('')}</div><footer class="archive-footer"><span>총 ${rows.length}개의 항목</span><div><button disabled>‹</button><button class="active">1</button><button>›</button></div></footer></section>`,
      section
    )
  }

  const settingsView = () =>
    shell(
      `<header class="page-heading"><div><span class="eyebrow">TEAM SETTINGS</span><h1>팀 스페이스 설정</h1><p>팀 정보, 접근 권한과 알림 정책을 관리하세요.</p></div><button class="primary" data-action="save-team">변경사항 저장</button></header><div class="team-settings-grid"><section class="settings-card"><header><i>${team.badge}</i><div><h2>기본 정보</h2><p>팀 멤버에게 표시되는 정보입니다.</p></div></header><label class="field">팀 스페이스 이름<input value="${team.name}"></label><label class="field">팀 설명<textarea>${team.description}</textarea></label><label class="field">팀 대표 색상<select>${team.colorOptions.map(color => `<option>${color}</option>`).join('')}</select></label></section><section class="settings-card"><header><i>♙</i><div><h2>접근 및 권한</h2><p>새 멤버의 기본 접근 수준을 설정합니다.</p></div></header><div class="setting-toggle"><span><b>초대 링크 활성화</b><small>링크를 가진 사용자가 가입할 수 있습니다.</small></span><button class="switch-on" data-action="toggle-switch"><i></i></button></div><div class="setting-toggle"><span><b>Owner 승인 필요</b><small>새 멤버 가입 전 승인을 요청합니다.</small></span><button data-action="toggle-switch"><i></i></button></div><label class="field">신규 멤버 기본 역할<select>${team.defaultMemberRoles.map(role => `<option>${role}</option>`).join('')}</select></label></section><section class="settings-card wide"><header><i>♢</i><div><h2>알림 설정</h2><p>팀 전체에 적용되는 기본 알림입니다.</p></div></header><div class="notification-grid"><label><input type="checkbox" checked> 회의 시작 10분 전 알림</label><label><input type="checkbox" checked> 새 문서 및 댓글 알림</label><label><input type="checkbox" checked> AI 요약 생성 완료 알림</label><label><input type="checkbox"> 주간 활동 리포트</label></div></section><section class="settings-card danger wide"><header><i>!</i><div><h2>위험 영역</h2><p>이 작업은 되돌릴 수 없으니 주의하세요.</p></div><button class="danger-btn">팀 스페이스 삭제</button></header></section></div>`,
      'settings'
    )

  return {
    schedule: scheduleView,
    members: membersView,
    documents: () => documentsView('documents'),
    minutes: () => documentsView('minutes'),
    summary: () => documentsView('summary'),
    feedback: () => documentsView('feedback'),
    settings: settingsView
  }
}

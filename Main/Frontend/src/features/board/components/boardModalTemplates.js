export function createBoardModalTemplates({
  getWorkspaces,
  inviteMembers,
  teamCreateColors
}) {
  return function renderModal(type) {
    if (type === 'team') {
      return `<div class="modal-backdrop"><form class="modal" data-submit="modal"><button class="close" type="button" data-action="close">×</button><small style="color:#4285ef">NEW TEAM SPACE</small><h2>새 팀 스페이스</h2><label class="field">팀 이름<input required></label><label class="field">설명<textarea></textarea></label><div class="colors"><span style="margin-right:auto"><b>팀 색상</b><br><small>팀 아이콘과 포인트 색상에 사용됩니다.</small></span>${teamCreateColors.map(color => `<button type="button" style="background:${color.value};border:${color.bordered ? '.8px solid #111' : '0'}"></button>`).join('')}</div><button class="primary block">팀 스페이스 만들기</button></form></div>`
    }

    if (type === 'meeting') {
      return `<div class="modal-backdrop"><form class="modal" data-submit="modal"><button class="close" type="button" data-action="close">×</button><small style="color:#4285ef">NEW MEETING</small><h2>회의 생성하기</h2><label class="field">회의 이름<input required></label><label class="field">담당 팀<select>${getWorkspaces().map(workspace => `<option value="${workspace.id}">${workspace.name}</option>`).join('')}</select></label><div style="height:84px"></div><button class="primary block">회의 만들기</button></form></div>`
    }

    return `<div class="modal-backdrop"><section class="modal invite-modal"><button class="close" data-action="close">×</button><h2>멤버 초대 및 공유</h2><p>프로젝트 팀원을 초대하세요.</p><div class="invite-row"><input placeholder="email@example.com"><select><option>편집 가능</option></select><button class="primary">초대 발송 →</button></div><div class="invite-list"><b>현재 멤버 ${inviteMembers.length}</b>${inviteMembers.map(member => `<article><i class="avatar">${member.avatarText}</i><span>${member.name}<br><small>${member.email}</small></span><em>${member.role}</em></article>`).join('')}</div><button class="outline-btn block">▣ 링크 복사</button></section></div>`
  }
}

const meetingControl = item =>
  `<button class="meeting-control ${item.active ? 'active' : ''}" data-control="${item.id}"><span><img src="/assets/icons/${item.id}.svg" alt="" aria-hidden="true"></span><small>${item.label}</small></button>`

export function createMeetingTemplates({
  app,
  state,
  activeMeeting,
  meetingRoom,
  meetingControls
}) {
  const directList = () =>
    `<div class="direct-list"><header><b>다이렉트 메시지</b><small>온라인 ${meetingRoom.directContacts.length}명</small></header>${meetingRoom.directContacts.map(contact => `<button type="button" class="direct-contact" data-direct-name="${contact.name}"><i>${contact.avatarText}</i><span><b>${contact.name}</b><small>${contact.preview}</small></span><em>${contact.time}</em></button>`).join('')}</div>`

  const directConversation = name => {
    const contact = meetingRoom.directContacts.find(item => item.name === name)
    if (!contact) return directList()
    return `<div class="direct-conversation"><header><button type="button" data-action="direct-back">←</button><div><b>${contact.name}</b><small>온라인</small></div></header>${contact.messages.map(message => `<article class="${message.mine ? 'me' : ''}"><b>${message.sender}　<small>${message.time}</small></b><p>${message.body}</p></article>`).join('')}</div>`
  }

  const meetingView = () =>
    `<main class="meeting-room ${state.darkMeeting ? 'dark' : ''}"><header class="meeting-top"><h1>🔴　${activeMeeting.roomTitle} (${activeMeeting.status})</h1><span><b>+${meetingRoom.totalParticipants}</b>　? 도움말</span></header><div class="meeting-content"><section class="video-column"><div class="video-grid">${meetingRoom.participants.slice(0, 4).map(participant => `<article class="video-tile"><div class="video-person">${participant.avatarText}</div><button class="meeting-pin" type="button" data-action="pin-participant" aria-label="${participant.displayName} 화면 고정" aria-pressed="false"></button><div class="participant-badge ${participant.muted ? 'is-muted' : ''}"><button type="button" data-action="toggle-participant-mic" aria-label="${participant.displayName} 마이크 ${participant.muted ? '켜기' : '음소거'}">${participant.muted ? '🔇' : '🎙'}</button><span>${participant.displayName}</span></div></article>`).join('')}</div><footer class="controls"><span class="meeting-agenda">${activeMeeting.agendaTime}　| ${activeMeeting.roomTitle}</span><div class="control-items">${meetingControls.map(meetingControl).join('')}</div><i class="control-divider"></i><button class="hangup" data-view="home" aria-label="통화 종료"><img src="/assets/icons/hangup.svg" alt="" aria-hidden="true"></button></footer></section><aside class="chat-panel"><nav class="chat-tabs"><button class="active">채팅</button><button>다이렉트</button></nav><section class="messages">${meetingRoom.chatMessages.map(message => `<article class="${message.mine ? 'me' : ''}"><b>${message.sender}　<small>${message.time}</small></b><p>${message.body}</p></article>`).join('')}</section><form class="chat-input" data-submit="chat"><input placeholder="메시지를 입력하세요..."><button>▷</button></form></aside></div></main>`

  const enhanceMeetingHeader = () => {
    const actions = app.querySelector('.meeting-top>span')
    if (!actions) return
    actions.className = 'meeting-top-actions'
    actions.innerHTML = `<button type="button" class="participant-count" data-action="show-participants"><span>${meetingRoom.participants.slice(1, 4).map(participant => `<i>${participant.avatarText.slice(0, 1)}</i>`).join('')}</span><b>${meetingRoom.totalParticipants}명 참가 중</b></button><button type="button" class="meeting-help" data-action="meeting-help"><i>?</i><b>도움말</b></button>`
  }

  const meetingInfoModal = type =>
    type === 'participants'
      ? `<div class="modal-backdrop"><section class="modal meeting-info-modal"><button class="close" type="button" data-action="close">×</button><small>MEETING PARTICIPANTS</small><h2>참가자 ${meetingRoom.totalParticipants}명</h2><div class="meeting-member-list">${meetingRoom.participants.map(participant => `<article><i>${participant.avatarText.slice(0, 1)}</i><span><b>${participant.displayName}</b><small>${participant.status}</small></span><em>${participant.role}</em></article>`).join('')}<p>외 ${meetingRoom.totalParticipants - meetingRoom.participants.length}명이 참여하고 있습니다.</p></div></section></div>`
      : '<div class="modal-backdrop"><section class="modal meeting-info-modal"><button class="close" type="button" data-action="close">×</button><small>MEETING HELP</small><h2>회의 도움말</h2><div class="meeting-help-content"><article><b>마이크와 카메라</b><p>하단 버튼을 눌러 마이크와 카메라를 켜거나 끌 수 있습니다.</p></article><article><b>화면 고정</b><p>참가자 영상에 마우스를 올린 뒤 핀 버튼을 누르면 화면을 고정할 수 있습니다.</p></article><article><b>채팅과 다이렉트</b><p>전체 채팅을 사용하거나 참가자를 선택해 1:1 메시지를 보낼 수 있습니다.</p></article><article><b>화면 공유</b><p>공유 버튼을 누르면 화면이나 특정 창을 팀원에게 공유할 수 있습니다.</p></article></div></section></div>'

  const meetingMoreMenu = () =>
    '<section class="meeting-more-menu"><header><b>더보기</b><small>회의 기능 설정</small></header><button type="button" data-more-option="blur"><i>◫</i><span><b>배경 흐리기</b><small>내 영상의 배경을 흐리게 표시</small></span><em>✓</em></button><button type="button" data-more-option="fullscreen"><i>⛶</i><span><b>전체 화면</b><small>회의 화면을 크게 보기</small></span><em>✓</em></button><hr><button type="button" data-more-option="settings"><i>⚙</i><span><b>회의 설정</b><small>오디오와 비디오 장치 설정</small></span><em>→</em></button></section>'

  return {
    meeting: meetingView,
    directList,
    directConversation,
    enhanceMeetingHeader,
    meetingInfoModal,
    meetingMoreMenu
  }
}

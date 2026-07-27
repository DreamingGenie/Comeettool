export function registerMeetingEvents({
  signal,
  getCurrentUser,
  notify,
  directList,
  directConversation,
  meetingInfoModal,
  meetingMoreMenu
}) {
  document.addEventListener(
    'click',
    event => {
      const control = event.target.closest('[data-control]')
      if (control) control.classList.toggle('active')

      const pin = event.target.closest('[data-action="pin-participant"]')
      if (pin) togglePinnedParticipant(pin, notify)

      const mic = event.target.closest('[data-action="toggle-participant-mic"]')
      if (mic) toggleParticipantMic(mic, notify)

      handleChatNavigation(event, directList, directConversation)

      const action = event.target.closest('[data-action]')?.dataset.action
      if (action === 'show-participants') {
        document.body.insertAdjacentHTML('beforeend', meetingInfoModal('participants'))
      }
      if (action === 'meeting-help') {
        document.body.insertAdjacentHTML('beforeend', meetingInfoModal('help'))
      }

      handleMoreMenu(event, meetingMoreMenu, notify)
    },
    { signal }
  )

  document.addEventListener(
    'submit',
    event => {
      if (event.target.dataset.submit !== 'chat') return
      event.preventDefault()
      const input = event.target.querySelector('input')
      if (!input.value.trim()) return

      document
        .querySelector('.messages')
        ?.insertAdjacentHTML(
          'beforeend',
          `<article class="me"><b>${getCurrentUser().nickname}　<small>방금</small></b><p>${input.value.replace(/[<>]/g, '')}</p></article>`
        )
      input.value = ''
    },
    { signal }
  )
}

function togglePinnedParticipant(pin, notify) {
  const participant = pin
    .closest('.video-tile')
    ?.querySelector('.participant-badge span')?.textContent
  const willPin = !pin.classList.contains('is-pinned')
  document.querySelectorAll('.meeting-pin').forEach(button => {
    button.classList.remove('is-pinned')
    button.setAttribute('aria-pressed', 'false')
  })
  if (willPin) {
    pin.classList.add('is-pinned')
    pin.setAttribute('aria-pressed', 'true')
  }
  notify(willPin ? `${participant} 화면을 고정했습니다.` : '화면 고정을 해제했습니다.')
}

function toggleParticipantMic(mic, notify) {
  const badge = mic.closest('.participant-badge')
  const muted = badge.classList.toggle('is-muted')
  mic.textContent = muted ? '🔇' : '🎙'
  mic.setAttribute(
    'aria-label',
    `${badge.querySelector('span').textContent} 마이크 ${muted ? '켜기' : '음소거'}`
  )
  notify(muted ? '마이크를 음소거했습니다.' : '마이크를 켰습니다.')
}

function handleChatNavigation(event, directList, directConversation) {
  const panel = document.querySelector('.chat-panel .messages')
  if (!panel) return
  const input = document.querySelector('.chat-input input')
  const tab = event.target.closest('.chat-tabs button')
  if (tab) {
    if (!panel.dataset.chatHtml) panel.dataset.chatHtml = panel.innerHTML
    tab.parentElement
      .querySelectorAll('button')
      .forEach(button => button.classList.toggle('active', button === tab))
    const isDirect = tab.textContent.trim() === '다이렉트'
    panel.innerHTML = isDirect ? directList() : panel.dataset.chatHtml
    input.disabled = isDirect
    input.placeholder = isDirect
      ? '대화 상대를 선택하세요...'
      : '메시지를 입력하세요...'
    return
  }

  const contact = event.target.closest('[data-direct-name]')
  if (contact) {
    const name = contact.dataset.directName
    panel.innerHTML = directConversation(name)
    panel.dataset.recipient = name
    input.disabled = false
    input.placeholder = `${name}에게 메시지 보내기...`
    input.focus()
    return
  }

  if (event.target.closest('[data-action="direct-back"]')) {
    panel.innerHTML = directList()
    delete panel.dataset.recipient
    input.disabled = true
    input.placeholder = '대화 상대를 선택하세요...'
  }
}

function handleMoreMenu(event, meetingMoreMenu, notify) {
  const more = event.target.closest('[data-control="more"]')
  const controls = event.target.closest('.controls')
  if (more) {
    const menu = controls.querySelector('.meeting-more-menu')
    if (menu) menu.remove()
    else controls.insertAdjacentHTML('beforeend', meetingMoreMenu())
    return
  }

  const option = event.target.closest('[data-more-option]')
  if (option) {
    if (option.dataset.moreOption === 'settings') {
      notify('회의 설정 화면을 준비했습니다.')
      return
    }
    const enabled = option.classList.toggle('enabled')
    notify(`${option.querySelector('b').textContent} 기능을 ${enabled ? '켰습니다.' : '껐습니다.'}`)
    return
  }

  if (!event.target.closest('.meeting-more-menu')) {
    document.querySelector('.meeting-more-menu')?.remove()
    document.querySelector('[data-control="more"]')?.classList.remove('active')
  }
}

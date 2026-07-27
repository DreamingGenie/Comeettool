export function registerBoardEvents({
  signal,
  boardStore,
  render,
  renderModal,
  runTask,
  notify
}) {
  document.addEventListener(
    'click',
    async event => {
      const section = event.target.closest('[data-team-section]')
      if (section) {
        render(section.dataset.teamSection)
        return
      }

      const filter = event.target.closest('.filter-tabs button')
      if (filter) {
        filter.parentElement
          .querySelectorAll('button')
          .forEach(button => button.classList.toggle('active', button === filter))
        return
      }

      const team = event.target.closest('.team-bubble:not(.add)')
      if (team) {
        team.parentElement
          .querySelectorAll('.team-bubble')
          .forEach(button => button.classList.toggle('active', button === team))
        notify(`${team.textContent.trim()} 팀을 선택했습니다.`)
        return
      }

      const monthButton = event.target.closest('.month-ctrl button')
      if (monthButton) {
        moveCalendarMonth(monthButton)
        return
      }

      const action = event.target.closest('[data-action]')?.dataset.action
      if (action === 'new-team') {
        document.body.insertAdjacentHTML('beforeend', renderModal('team'))
      }
      if (action === 'new-meeting') {
        document.body.insertAdjacentHTML('beforeend', renderModal('meeting'))
      }
      if (action === 'new-document') notify('새 문서 작성 화면을 준비했습니다.')
      if (action === 'open-document') notify('문서를 열었습니다.')
      if (action === 'save-team') {
        await runTask(
          () => Promise.resolve({ success: true }),
          () => notify('팀 설정을 저장했습니다.')
        )
      }
      if (action === 'toggle-switch') {
        event.target.closest('button')?.classList.toggle('switch-on')
      }
      if (action === 'invite') {
        document.body.insertAdjacentHTML('beforeend', renderModal('invite'))
      }
      if (action === 'close') {
        event.target.closest('.modal-backdrop')?.remove()
      }
    },
    { signal }
  )

  document.addEventListener(
    'submit',
    async event => {
      if (event.target.dataset.submit !== 'modal') return
      event.preventDefault()
      const isTeam = event.target.textContent.includes('팀 스페이스')
      const fields = event.target.querySelectorAll('input,textarea,select')
      const payload = {
        name: fields[0]?.value || '새 항목',
        description: fields[1]?.value || ''
      }

      await runTask(
        () =>
          isTeam
            ? boardStore.createWorkspace(payload)
            : boardStore.createMeeting(payload),
        () => {
          event.target.closest('.modal-backdrop')?.remove()
          notify('성공적으로 만들었습니다.')
        }
      )
    },
    { signal }
  )

  document.addEventListener(
    'input',
    event => {
      if (event.target.matches('.workspace-search input')) {
        const query = event.target.value.trim().toLowerCase()
        document.querySelectorAll('.workspace-item').forEach(item => {
          item.hidden = !item.textContent.toLowerCase().includes(query)
        })
      }

      if (event.target.matches('.table-tools input,.archive-tools input')) {
        const query = event.target.value.trim().toLowerCase()
        const container = event.target.closest('.member-table,.archive-panel')
        container
          ?.querySelectorAll('.member-row:not(.head),.archive-row')
          .forEach(row => {
            row.hidden = !row.textContent.toLowerCase().includes(query)
          })
      }
    },
    { signal }
  )
}

function moveCalendarMonth(button) {
  const label = button.parentElement.querySelector('b')
  const match = label?.textContent.match(/(\d+)년\s*(\d+)월/)
  if (!match) return

  let year = Number(match[1])
  let month =
    Number(match[2]) + (button === button.parentElement.firstElementChild ? -1 : 1)
  if (month < 1) {
    month = 12
    year -= 1
  }
  if (month > 12) {
    month = 1
    year += 1
  }
  label.textContent = `${year}년 ${month}월`
}

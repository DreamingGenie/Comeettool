export function registerAuthEvents({
  signal,
  state,
  onboardingMock,
  authStore,
  runTask,
  render,
  setCurrentUser
}) {
  document.addEventListener(
    'click',
    async event => {
      const choice = event.target.closest('[data-choice]')
      if (choice) {
        const group = choice.closest('.choice-grid,.choice-row')
        group
          ?.querySelectorAll('[data-choice]')
          .forEach(button => button.classList.toggle('active', button === choice))
        const selected = Number(choice.dataset.choice)
        if (Number.isFinite(selected)) state.selected = selected
        return
      }

      const action = event.target.closest('[data-action]')?.dataset.action
      if (action === 'show-demo') render('home')
      if (action === 'next-step') {
        if (state.step < onboardingMock.steps.length - 1) {
          state.step += 1
          state.selected = 0
          render('onboarding')
        } else {
          render('home')
        }
      }
      if (action === 'logout') {
        await runTask(() => authStore.logout(), () => render('intro'))
      }
    },
    { signal }
  )

  document.addEventListener(
    'submit',
    async event => {
      if (event.target.dataset.submit !== 'auth') return
      event.preventDefault()
      const payload = Object.fromEntries(new FormData(event.target))
      await runTask(
        () => (state.view === 'signup' ? authStore.signup(payload) : authStore.login(payload)),
        result => {
          setCurrentUser(result.user)
          state.step = 0
          render('onboarding')
        }
      )
    },
    { signal }
  )
}

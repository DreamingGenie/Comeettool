export function registerUserEvents({
  signal,
  userStore,
  runTask,
  render,
  notify,
  setCurrentUser
}) {
  document.addEventListener(
    'submit',
    async event => {
      if (event.target.dataset.submit !== 'save') return
      event.preventDefault()
      const payload = Object.fromEntries(new FormData(event.target))

      if (event.target.classList.contains('profile-settings')) {
        await runTask(
          () => userStore.updateProfile(payload),
          result => {
            setCurrentUser(result)
            notify('프로필을 저장했습니다.')
            render('profile')
          }
        )
        return
      }

      await runTask(
        () => userStore.changePassword(payload),
        () => notify('비밀번호를 변경했습니다.')
      )
    },
    { signal }
  )
}

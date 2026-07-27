export function registerNavigationEvents({ signal, render }) {
  document.addEventListener(
    'click',
    event => {
      const view = event.target.closest('[data-view]')
      if (view) {
        event.preventDefault()
        render(view.dataset.view)
        return
      }

      if (event.target.closest('[data-action="eye"]')) {
        const input = event.target.closest('.password-box')?.querySelector('input')
        if (input) input.type = input.type === 'password' ? 'text' : 'password'
      }
    },
    { signal }
  )
}

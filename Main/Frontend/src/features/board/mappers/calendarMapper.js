const WEEKDAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

const toNumber = (value, fallback) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const getEventDay = event => {
  const explicitDay = Number(event?.day)
  if (Number.isInteger(explicitDay)) return explicitDay

  const dateValue = event?.date || event?.startAt || event?.startTime
  if (!dateValue) return null

  const date = new Date(dateValue)
  return Number.isNaN(date.getTime()) ? null : date.getDate()
}

const toCalendarEvent = event => ({
  ...event,
  day: getEventDay(event)
})

export function toCalendarViewModel(payload = {}, fallback = {}) {
  const source = Array.isArray(payload) ? { events: payload } : payload || {}
  const now = new Date()
  const year = toNumber(source.year, toNumber(fallback.year, now.getFullYear()))
  const month = toNumber(
    source.month,
    toNumber(fallback.month, now.getMonth() + 1)
  )

  return {
    year,
    month,
    leadingBlankDays: new Date(year, month - 1, 1).getDay(),
    totalDays: new Date(year, month, 0).getDate(),
    weekdays: [...WEEKDAYS],
    events: (source.events || []).map(toCalendarEvent).filter(event => event.day)
  }
}

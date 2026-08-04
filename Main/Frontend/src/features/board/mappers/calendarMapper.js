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

export const toCalendarEventViewModel = (event, index = 0) => {
  const day = getEventDay(event)
  return {
    ...event,
    id: event?.id ?? event?.scheduleId ?? `calendar-${day}-${index}-${event?.title || 'event'}`,
    day,
    color: /^#[0-9a-f]{6}$/i.test(event?.color || '') ? event.color : '#7086E8'
  }
}

const isEventInMonth = (event, year, month) => {
  const startValue = event?.date || event?.startAt || event?.startTime
  if (!startValue) return true
  const start = new Date(startValue)
  const end = new Date(event?.endAt || event?.endTime || startValue)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return false
  const monthStart = new Date(year, month - 1, 1)
  const monthEnd = new Date(year, month, 0, 23, 59, 59, 999)
  return start <= monthEnd && end >= monthStart
}

export const toScheduleRequest = schedule => ({
  category: schedule.category || null,
  color: /^#[0-9a-f]{6}$/i.test(schedule.color || '') ? schedule.color : '#7086E8',
  title: schedule.title || null,
  description: schedule.description || null,
  startTime: schedule.startTime ? new Date(schedule.startTime).toISOString() : null,
  endTime: schedule.endTime ? new Date(schedule.endTime).toISOString() : null,
  userIdArr: Array.isArray(schedule.userIdArr) ? schedule.userIdArr.map(Number) : []
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
    events: (Array.isArray(source.events) ? source.events : [])
      .filter(event => isEventInMonth(event, year, month))
      .map(toCalendarEventViewModel)
      .filter(event => event.day)
  }
}

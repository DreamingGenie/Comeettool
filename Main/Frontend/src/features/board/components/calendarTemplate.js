export function renderCalendar(calendarData) {
  const cellCount = Math.ceil(
    (calendarData.leadingBlankDays + calendarData.totalDays) / 7
  ) * 7
  const cells = Array.from(
    { length: cellCount },
    (_, index) => index - calendarData.leadingBlankDays + 1
  )

  return `<section class="calendar"><header class="calendar-head"><h2>팀 일정</h2><div class="month-ctrl"><button>‹</button><b>${calendarData.year}년 ${calendarData.month}월</b><button>›</button></div></header><div class="calendar-grid">${calendarData.weekdays.map(day => `<b>${day}</b>`).join('')}${cells.map(day => `<div class="day ${day < 1 || day > calendarData.totalDays ? 'muted' : ''}">${day > 0 && day <= calendarData.totalDays ? day : ''}${calendarData.events.filter(event => event.day === day).map(event => `<span class="event ${event.tone === 'default' ? '' : event.tone}">${event.title}</span>`).join('')}</div>`).join('')}</div></section>`
}

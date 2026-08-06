<template>
  <section
    class="calendar"
    :style="{ '--calendar-week-count': weeks.length }"
  >
    <header class="calendar-head">
      <h2>{{ title }}</h2>
      <div class="month-ctrl">
        <button type="button" aria-label="이전 달" @click="$emit('change-month', -1)">‹</button>
        <b>{{ calendar.year }}년 {{ calendar.month }}월</b>
        <button type="button" aria-label="다음 달" @click="$emit('change-month', 1)">›</button>
      </div>
    </header>
    <div class="calendar-grid">
      <b v-for="weekday in calendar.weekdays" :key="weekday">{{ weekday }}</b>
      <section v-for="(week, weekIndex) in weeks" :key="weekIndex" class="calendar-week">
        <div
          v-for="(day, dayIndex) in week"
          :key="dayIndex"
          class="day"
          :class="{
            muted: !isCurrentMonthDay(day),
            'has-events': isCurrentMonthDay(day) && segmentsCovering(day).length
          }"
        >
          <template v-if="isCurrentMonthDay(day)">
            <span class="day-number">{{ day }}</span>
            <button
              v-if="editable"
              class="add-event"
              type="button"
              :aria-label="`${calendar.month}월 ${day}일 일정 추가`"
              @click.stop="$emit('select-date', day)"
            >
              ＋
            </button>
            <button
              v-if="hiddenSegmentsFor(day).length"
              class="more-events"
              type="button"
              :aria-label="`${calendar.month}월 ${day}일 숨겨진 일정 ${hiddenSegmentsFor(day).length}개 보기`"
            >
              +{{ hiddenSegmentsFor(day).length }}개
            </button>
            <section v-if="hiddenSegmentsFor(day).length" class="event-overflow-popover">
              <header><b>{{ calendar.month }}월 {{ day }}일</b><small>일정 {{ segmentsCovering(day).length }}개</small></header>
              <button
                v-for="segment in segmentsCovering(day)"
                :key="`popover-${segment.event.id || segment.event.title}-${segment.startDay}`"
                type="button"
                :disabled="!editable"
                @click.stop="$emit('select-event', segment.event)"
              >
                <i :style="{ backgroundColor: segment.event.color || '#7086E8' }"></i>
                <span>{{ segment.event.title }}</span>
              </button>
            </section>
          </template>
        </div>
        <div class="week-event-layer" aria-label="주간 일정">
          <button
            v-for="segment in visibleSegmentsForWeek(weekIndex)"
            :key="`${segment.event.id || segment.event.title}-${segment.startDay}`"
            class="event"
            :class="[
              segment.event.tone === 'default' ? '' : segment.event.tone,
              { 'continues-before': segment.continuesBefore, 'continues-after': segment.continuesAfter }
            ]"
            :style="{
              gridColumn: `${segment.startColumn} / span ${segment.span}`,
              gridRow: segment.lane + 1,
              backgroundColor: segment.event.color || '#7086E8'
            }"
            type="button"
            :disabled="!editable"
            @click.stop="$emit('select-event', segment.event)"
          >
            {{ segment.event.title }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  calendar: {
    type: Object,
    default: () => ({
      year: 0,
      month: 0,
      leadingBlankDays: 0,
      totalDays: 0,
      weekdays: [],
      events: []
    })
  },
  title: { type: String, default: '팀 일정' },
  editable: { type: Boolean, default: false }
})

defineEmits(['change-month', 'select-date', 'select-event'])

const days = computed(() => {
  const count = Math.ceil(
    (props.calendar.leadingBlankDays + props.calendar.totalDays) / 7
  ) * 7
  return Array.from(
    { length: count },
    (_, index) => index - props.calendar.leadingBlankDays + 1
  )
})

const weeks = computed(() => {
  const result = []
  for (let index = 0; index < days.value.length; index += 7) {
    result.push(days.value.slice(index, index + 7))
  }
  return result
})

const isCurrentMonthDay = day => day > 0 && day <= props.calendar.totalDays

const toValidDate = value => {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

const eventSegments = computed(() => {
  const year = props.calendar.year
  const monthIndex = props.calendar.month - 1
  const monthStart = new Date(year, monthIndex, 1)
  const monthEnd = new Date(year, monthIndex, props.calendar.totalDays, 23, 59, 59, 999)

  return props.calendar.events.flatMap(event => {
    let start = toValidDate(event.startTime || event.startAt || event.date)
    let end = toValidDate(event.endTime || event.endAt) || start

    if (!start) {
      const day = Number(event.day)
      if (!Number.isInteger(day)) return []
      start = new Date(year, monthIndex, day)
      end = start
    }

    if (end < monthStart || start > monthEnd) return []
    const visibleStart = start < monthStart ? monthStart : start
    const visibleEnd = end > monthEnd ? monthEnd : end
    let startDay = visibleStart.getDate()
    const endDay = visibleEnd.getDate()
    const segments = []

    while (startDay <= endDay) {
      const weekday = new Date(year, monthIndex, startDay).getDay()
      const segmentEnd = Math.min(endDay, startDay + (6 - weekday))
      segments.push({
        event,
        startDay,
        weekIndex: Math.floor((props.calendar.leadingBlankDays + startDay - 1) / 7),
        startColumn: weekday + 1,
        span: segmentEnd - startDay + 1,
        continuesBefore: startDay > visibleStart.getDate() || start < monthStart,
        continuesAfter: segmentEnd < endDay || end > monthEnd
      })
      startDay = segmentEnd + 1
    }

    return segments
  })
})

const maxVisibleLanes = 2
const laidOutSegments = computed(() => {
  const byWeek = new Map()
  eventSegments.value.forEach(segment => {
    const weekSegments = byWeek.get(segment.weekIndex) || []
    weekSegments.push(segment)
    byWeek.set(segment.weekIndex, weekSegments)
  })

  return [...byWeek.values()].flatMap(segments => {
    const laneEnds = []
    return [...segments]
      .sort((left, right) => left.startColumn - right.startColumn || right.span - left.span)
      .map(segment => {
        let lane = laneEnds.findIndex(endColumn => endColumn < segment.startColumn)
        if (lane < 0) lane = laneEnds.length
        laneEnds[lane] = segment.startColumn + segment.span - 1
        return { ...segment, lane }
      })
  })
})

const visibleSegmentsForWeek = weekIndex => laidOutSegments.value.filter(
  segment => segment.weekIndex === weekIndex && segment.lane < maxVisibleLanes
)

const segmentsCovering = day => {
  if (!isCurrentMonthDay(day)) return []
  return laidOutSegments.value.filter(
    segment => day >= segment.startDay && day < segment.startDay + segment.span
  )
}

const hiddenSegmentsFor = day => segmentsCovering(day).filter(
  segment => segment.lane >= maxVisibleLanes
)
</script>

<style scoped>
.calendar {
  --calendar-cell-padding: 8px;
  border: 1px solid #c8d3f5;
  background: #fff;
  box-shadow:
    0 14px 34px #314c8d14,
    0 0 0 1px #7086e80a;
  transition:
    border-color 0.28s ease,
    box-shadow 0.28s ease;
}

.calendar:hover {
  border-color: #aebced;
  box-shadow:
    0 16px 38px #314c8d1c,
    0 0 0 1px #7086e817;
}

:global(.app-shell) .calendar {
  --calendar-cell-padding: 6.4px;
}

.calendar-week {
  position: relative;
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.calendar-head {
  border-bottom-color: #d9e0f1;
  background: #fdfdff;
  transition: background-color 0.22s ease;
}

.calendar-head h2 {
  color: #7086e8;
}

.calendar:hover .calendar-head {
  background: #f8faff;
}

.month-ctrl button {
  display: grid;
  place-items: center;
  min-width: 28px;
  min-height: 28px;
  border-color: #d8deed;
  background: #fbfcff;
  color: #26324b;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.month-ctrl button:hover {
  border-color: #7086e8;
  background: #7086e8;
  color: #fff;
  box-shadow: 0 5px 12px #4059ca38;
  transform: translateY(-2px) scale(1.05);
}

.month-ctrl button:active {
  box-shadow: 0 2px 6px #4059ca2c;
  transform: translateY(0) scale(0.96);
}

.month-ctrl button:focus-visible {
  outline: 2px solid #7086e8;
  outline-offset: 3px;
}

.day {
  position: relative;
  min-width: 0;
  padding-right: var(--calendar-cell-padding);
  padding-left: var(--calendar-cell-padding);
  overflow: visible !important;
  background: #fff;
  border-color: #dce2ef;
  transition:
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.day:not(.muted):hover {
  background: #f8faff;
  box-shadow: inset 0 0 0 1px #7086e85c;
}

.calendar-grid > b {
  border-bottom-color: #dce2ef;
  color: #8a91a2;
  background: #fdfdff;
}

.day.muted {
  background: #f7f8fb;
}

.day-number {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  border-radius: 50%;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.day:not(.muted):hover .day-number {
  background: #7086e8;
  color: #fff;
  font-weight: 700;
  transform: scale(1.08);
}

.add-event {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 4;
  display: grid;
  width: 22px;
  height: 22px;
  padding: 0;
  place-items: center;
  border: 1px solid #7086e8;
  border-radius: 50%;
  background: #7086e8;
  color: #fff;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  opacity: 0;
  transform: translateY(-3px) scale(0.9);
  transition: opacity 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.day:not(.muted):hover .add-event,
.day:not(.muted):focus-within .add-event {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.add-event:hover,
.add-event:focus-visible {
  box-shadow: 0 5px 12px #4059ca45;
  outline: none;
}

.day.has-events .day-number {
  color: #5870dc;
  font-weight: 700;
}

.day.has-events:hover .day-number {
  color: #fff;
}

.event {
  position: relative;
  min-width: 0;
  height: 15px;
  margin: 0 var(--calendar-cell-padding);
  padding: 2px 4px;
  border: 0;
  overflow: hidden;
  text-align: left;
  cursor: pointer;
  transform-origin: center;
  transition:
    filter 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.week-event-layer {
  position: absolute;
  top: 27px;
  right: 0;
  left: 0;
  z-index: 3;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  grid-auto-rows: 15px;
  gap: 3px 0;
  pointer-events: none;
}

.week-event-layer .event {
  pointer-events: auto;
}

.event.continues-before {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.event.continues-after {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.event:disabled {
  cursor: default;
}

.event::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 15%, #ffffff42 48%, transparent 78%);
  content: '';
  opacity: 0;
  transform: translateX(-105%);
  transition:
    opacity 0.2s ease,
    transform 0.32s ease;
}

.event:hover {
  filter: brightness(1.08) saturate(1.08);
  box-shadow: 0 5px 12px #3049a446;
  transform: translateY(-2px) scale(1.015);
}

.event:hover::after {
  opacity: 1;
  transform: translateX(105%);
}

.more-events {
  position: absolute;
  right: var(--calendar-cell-padding);
  bottom: 4px;
  z-index: 4;
  display: block;
  width: auto;
  margin: 0;
  padding: 2px 4px;
  overflow: hidden;
  border: 0;
  border-radius: 4px;
  background: #eef1f8;
  color: #606a7d;
  cursor: default;
  font-size: 8px;
  font-weight: 700;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-overflow-popover {
  position: absolute;
  top: 28px;
  left: 7px;
  z-index: 30;
  display: grid;
  gap: 5px;
  width: max(190px, calc(100% - 14px));
  max-height: 230px;
  padding: 10px;
  overflow-y: auto;
  border: 1px solid #d8deeb;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 14px 34px rgba(34, 48, 83, 0.2);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-5px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.day:nth-child(7) .event-overflow-popover {
  right: 7px;
  left: auto;
}

.more-events:hover + .event-overflow-popover,
.more-events:focus-visible + .event-overflow-popover,
.event-overflow-popover:hover,
.event-overflow-popover:focus-within {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.event-overflow-popover header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 5px;
  border-bottom: 1px solid #edf0f5;
}

.event-overflow-popover header b {
  color: #222a3b;
  font-size: 10px;
}

.event-overflow-popover header small {
  color: #8790a2;
  font-size: 8px;
}

.event-overflow-popover > button {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  align-items: center;
  gap: 7px;
  min-height: 27px;
  padding: 5px 7px;
  border: 0;
  border-radius: 7px;
  background: #f7f8fb;
  color: #31394b;
  font-size: 9px;
  text-align: left;
}

.event-overflow-popover > button:not(:disabled) {
  cursor: pointer;
}

.event-overflow-popover > button:not(:disabled):hover {
  background: #eef1f8;
}

.event-overflow-popover > button i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.event-overflow-popover > button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .calendar,
  .calendar-head,
  .month-ctrl button,
  .day,
  .day-number,
  .event,
  .event::after {
    transition: none;
  }

  .calendar:hover,
  .month-ctrl button:hover,
  .month-ctrl button:active,
  .day:not(.muted):hover .day-number,
  .event:hover {
    transform: none;
  }
}
</style>

<template>
  <section class="calendar">
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
      <div
        v-for="(day, index) in days"
        :key="index"
        class="day"
        :class="{
          muted: day < 1 || day > calendar.totalDays,
          'has-events': day > 0 && day <= calendar.totalDays && eventsFor(day).length,
          'segment-origin': day > 0 && day <= calendar.totalDays && segmentsFor(day).length
        }"
      >
        <template v-if="day > 0 && day <= calendar.totalDays">
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
            v-for="segment in segmentsFor(day)"
            :key="`${segment.event.id || segment.event.title}-${segment.startDay}`"
            class="event"
            :class="[
              segment.event.tone === 'default' ? '' : segment.event.tone,
              { 'continues-before': segment.continuesBefore, 'continues-after': segment.continuesAfter }
            ]"
            :style="{ width: `calc(${segment.span * 100}% + ${(segment.span - 1) * 12.8}px)` }"
            type="button"
            :disabled="!editable"
            @click.stop="$emit('select-event', segment.event)"
          >
            {{ segment.event.title }}
          </button>
        </template>
      </div>
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

const eventsFor = day => props.calendar.events.filter(event => event.day === day)

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
        span: segmentEnd - startDay + 1,
        continuesBefore: startDay > visibleStart.getDate() || start < monthStart,
        continuesAfter: segmentEnd < endDay || end > monthEnd
      })
      startDay = segmentEnd + 1
    }

    return segments
  })
})

const segmentsFor = day => eventSegments.value.filter(segment => segment.startDay === day)
</script>

<style scoped>
.calendar {
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
  z-index: 0;
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

.day.segment-origin {
  z-index: 2;
  overflow: visible;
}

.day.segment-origin:hover {
  z-index: 3;
}

.add-event {
  position: absolute;
  top: 6px;
  right: 6px;
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
  width: 100%;
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

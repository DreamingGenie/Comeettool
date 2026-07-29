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
          'has-events': day > 0 && day <= calendar.totalDays && eventsFor(day).length
        }"
      >
        <template v-if="day > 0 && day <= calendar.totalDays">
          <span class="day-number">{{ day }}</span>
          <span
            v-for="event in eventsFor(day)"
            :key="event.id || `${day}-${event.title}`"
            class="event"
            :class="event.tone === 'default' ? '' : event.tone"
          >
            {{ event.title }}
          </span>
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
  title: { type: String, default: '팀 일정' }
})

defineEmits(['change-month'])

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
</script>

<style scoped>
.calendar {
  transition:
    border-color 0.28s ease,
    box-shadow 0.28s ease;
}

.calendar:hover {
  border-color: #aebbed;
  box-shadow:
    0 10px 26px #263b761c,
    0 0 0 1px #7188ff1c;
}

.calendar-head {
  transition: background-color 0.22s ease;
}

.calendar:hover .calendar-head {
  background: #fafbff;
}

.month-ctrl button {
  display: grid;
  place-items: center;
  min-width: 28px;
  min-height: 28px;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.month-ctrl button:hover {
  border-color: #7188ff;
  background: #6277e8;
  color: #fff;
  box-shadow: 0 5px 12px #4059ca38;
  transform: translateY(-2px) scale(1.05);
}

.month-ctrl button:active {
  box-shadow: 0 2px 6px #4059ca2c;
  transform: translateY(0) scale(0.96);
}

.month-ctrl button:focus-visible {
  outline: 2px solid #7188ff;
  outline-offset: 3px;
}

.day {
  position: relative;
  z-index: 0;
  background: #fff;
  transition:
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.day:not(.muted):hover {
  background: #f7f8fe;
  box-shadow: inset 0 0 0 1px #7188ff66;
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
  background: #6679ea;
  color: #fff;
  font-weight: 700;
  transform: scale(1.08);
}

.day.has-events .day-number {
  color: #4d63d8;
  font-weight: 700;
}

.day.has-events:hover .day-number {
  color: #fff;
}

.event {
  position: relative;
  overflow: hidden;
  transform-origin: center;
  transition:
    filter 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
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

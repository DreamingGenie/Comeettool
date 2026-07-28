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
        :class="{ muted: day < 1 || day > calendar.totalDays }"
      >
        <template v-if="day > 0 && day <= calendar.totalDays">
          {{ day }}
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

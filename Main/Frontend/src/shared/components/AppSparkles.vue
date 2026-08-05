<template>
  <span class="app-sparkles" aria-hidden="true">
    <i
      v-for="(star, index) in stars"
      :key="index"
      class="app-sparkles__star"
      :class="`app-sparkles__star--${star.tone || 'blue'}`"
      :style="{
        '--sparkle-x': `${star.x}%`,
        '--sparkle-y': `${star.y}%`,
        '--sparkle-size': `${star.size}px`,
        '--sparkle-duration': `${star.duration}s`,
        '--sparkle-delay': `${star.delay}s`
      }"
    >
      ✦
    </i>
    <i
      v-for="(planet, index) in planets"
      :key="`planet-${index}`"
      class="app-sparkles__planet"
      :class="`app-sparkles__planet--${planet.type}`"
      :style="{
        '--planet-x': `${planet.x}%`,
        '--planet-y': `${planet.y}%`,
        '--planet-size': `${planet.size}px`,
        '--planet-color': planet.color,
        '--planet-duration': `${planet.duration}s`,
        '--planet-delay': `${planet.delay}s`
      }"
    ></i>
    <i
      v-if="cometVisible"
      :key="cometCycle"
      class="app-sparkles__comet"
      :style="{
        '--comet-x': `${currentComet.x}%`,
        '--comet-y': `${currentComet.y}%`,
        '--comet-size': `${currentComet.size}px`,
        '--comet-duration': `${currentComet.duration}s`,
        '--comet-delay': `${currentComet.delay}s`,
        '--comet-color': currentComet.color
      }"
      @animationend="queueNextComet"
    ></i>
  </span>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const stars = [
  { x: 18, y: 26, size: 4, duration: 4.8, delay: -1.2, tone: 'yellow' },
  { x: 25, y: 70, size: 6, duration: 5.6, delay: -3.7, tone: 'blue' },
  { x: 33, y: 22, size: 4, duration: 4.2, delay: -2.1, tone: 'white' },
  { x: 40, y: 68, size: 5, duration: 6.1, delay: -4.4, tone: 'yellow' },
  { x: 48, y: 28, size: 4, duration: 5.2, delay: -0.8, tone: 'blue' },
  { x: 55, y: 72, size: 6, duration: 4.6, delay: -3.1, tone: 'yellow' },
  { x: 63, y: 24, size: 4, duration: 6.4, delay: -5.2, tone: 'blue' },
  { x: 70, y: 68, size: 5, duration: 5.8, delay: -2.8, tone: 'white' },
  { x: 77, y: 28, size: 6, duration: 6.6, delay: -4.9, tone: 'yellow' },
  { x: 83, y: 72, size: 4, duration: 4.9, delay: -1.7, tone: 'blue' },
  { x: 88, y: 24, size: 5, duration: 5.5, delay: -3.9, tone: 'yellow' },
  { x: 91, y: 66, size: 4, duration: 6.2, delay: -0.5, tone: 'white' }
]

const planets = [
  {
    x: 60,
    y: 46,
    size: 10,
    color: '#7188ff',
    type: 'ringed',
    duration: 11,
    delay: -4
  },
  {
    x: 82,
    y: 44,
    size: 8,
    color: '#d7b85a',
    type: 'moon',
    duration: 14,
    delay: -7
  }
]

const cometLanes = [
  { x: 22, y: -10, size: 23, color: '#EAF0FF' },
  { x: 46, y: 4, size: 27, color: '#FFFFFF' },
  { x: 68, y: -8, size: 29, color: '#FFF9B8' },
  { x: 88, y: 2, size: 25, color: '#D8E4FF' },
  { x: 96, y: -12, size: 28, color: '#FFFFFF' }
]

const cometLaneIndex = ref(2)
const cometVisible = ref(true)
const cometCycle = ref(0)
let cometTimer

const currentComet = computed(() => ({
  ...cometLanes[cometLaneIndex.value],
  duration: 2.5,
  delay: 0
}))

function queueNextComet() {
  cometVisible.value = false
  clearTimeout(cometTimer)
  cometTimer = setTimeout(() => {
    cometLaneIndex.value = (cometLaneIndex.value + 2) % cometLanes.length
    cometCycle.value += 1
    cometVisible.value = true
  }, 400)
}

onBeforeUnmount(() => clearTimeout(cometTimer))
</script>

<style scoped>
.app-sparkles {
  position: absolute;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  border-radius: inherit;
  pointer-events: none;
}

.app-sparkles__star {
  position: absolute;
  top: var(--sparkle-y);
  left: var(--sparkle-x);
  color: #dbe5ff;
  font-size: var(--sparkle-size);
  font-style: normal;
  line-height: 1;
  opacity: 0;
  filter: drop-shadow(0 0 3px #9db6ff80);
  transform: translate(-50%, -50%) scale(0.55) rotate(-8deg);
  animation: app-sparkle-twinkle var(--sparkle-duration) ease-in-out var(--sparkle-delay) infinite;
}

.app-sparkles__star--yellow {
  color: #fffbd1;
  filter: drop-shadow(0 0 4px #eaff00a0);
}

.app-sparkles__star--blue {
  color: #9eb5ff;
}

.app-sparkles__star--white {
  color: #f4f7ff;
  filter: drop-shadow(0 0 3px #ffffff80);
}

.app-sparkles__planet {
  position: absolute;
  top: var(--planet-y);
  left: var(--planet-x);
  width: var(--planet-size);
  height: var(--planet-size);
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 25%, #ffffffb8 0 7%, transparent 9%),
    radial-gradient(circle at 34% 30%, color-mix(in srgb, var(--planet-color), white 38%) 0 12%, var(--planet-color) 42%, #1a284b 100%);
  box-shadow:
    inset -2px -3px 5px #0f1d3d80,
    0 0 8px color-mix(in srgb, var(--planet-color), transparent 45%);
  opacity: 0.38;
  transform: translate(-50%, -50%);
  animation: app-planet-float var(--planet-duration) ease-in-out var(--planet-delay) infinite;
}

.app-sparkles__planet--ringed::after {
  position: absolute;
  top: 38%;
  left: -34%;
  width: 164%;
  height: 34%;
  border: 1px solid #c8d5ffb8;
  border-radius: 50%;
  content: '';
  box-shadow: 0 0 3px #97afff55;
  transform: rotate(-18deg);
}

.app-sparkles__planet--moon {
  background:
    radial-gradient(circle at 32% 30%, #fff7b866 0 8%, transparent 10%),
    radial-gradient(circle at 68% 64%, #25365b80 0 13%, transparent 15%),
    radial-gradient(circle at 42% 48%, var(--planet-color), #675834 68%, #1a284b 100%);
}

.app-sparkles__comet {
  position: absolute;
  top: var(--comet-y);
  left: var(--comet-x);
  width: var(--comet-size);
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--comet-color), #b9c9ff88 34%, transparent 100%);
  opacity: 0;
  filter: drop-shadow(0 0 4px color-mix(in srgb, var(--comet-color), transparent 30%));
  transform: translate3d(0, -8px, 0) rotate(-34deg) scaleX(0.7);
  transform-origin: left center;
  animation: app-comet-pass var(--comet-duration) linear var(--comet-delay) forwards;
  will-change: opacity, transform;
}

.app-sparkles__comet::before {
  position: absolute;
  top: 50%;
  left: -1px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--comet-color);
  box-shadow: 0 0 6px var(--comet-color);
  content: '';
  transform: translateY(-50%);
}

.app-sparkles__comet::after {
  position: absolute;
  top: 50%;
  left: 4px;
  width: calc(var(--comet-size) * 1.35);
  height: 1px;
  background: linear-gradient(90deg, #a8bcff66, transparent);
  content: '';
  transform: translateY(-50%);
}

@keyframes app-sparkle-twinkle {
  0%,
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.55) rotate(-8deg);
  }

  38% {
    opacity: 0.18;
  }

  50% {
    opacity: 0.72;
    transform: translate(-50%, -54%) scale(1) rotate(7deg);
  }

  64% {
    opacity: 0.14;
  }
}

@keyframes app-planet-float {
  0%,
  100% {
    opacity: 0.28;
    transform: translate(-50%, -50%) rotate(-3deg) translateY(1px);
  }

  50% {
    opacity: 0.48;
    transform: translate(-50%, -50%) rotate(3deg) translateY(-3px);
  }
}

@keyframes app-comet-pass {
  0% {
    opacity: 0;
    transform: translate3d(0, -8px, 0) rotate(-34deg) scaleX(0.7);
  }

  8% {
    opacity: 0.95;
  }

  72% {
    opacity: 0.8;
  }

  100% {
    opacity: 0;
    transform: translate3d(-24vw, 78px, 0) rotate(-34deg) scaleX(1.08);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-sparkles__star,
  .app-sparkles__planet,
  .app-sparkles__comet {
    animation: none;
  }

  .app-sparkles__star {
    opacity: 0.18;
    transform: translate(-50%, -50%) scale(0.8);
  }

  .app-sparkles__planet {
    opacity: 0.34;
    transform: translate(-50%, -50%);
  }

  .app-sparkles__comet {
    display: none;
  }
}
</style>

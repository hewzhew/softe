<template>
  <div class="vehicle-token" :class="modeClass">
    <strong>{{ vehicle.id }}</strong>
    <span>{{ modeLabel }}</span>
    <small>{{ chargedKwh }} / {{ requestKwh }} 度</small>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  vehicle: { type: Object, required: true }
})

const modeLabel = computed(() => {
  if (props.vehicle.mode === 'FAST') {
    return '快充'
  }
  if (props.vehicle.mode === 'SLOW') {
    return '慢充'
  }
  return '未知'
})

const modeClass = computed(() => {
  if (props.vehicle.mode === 'FAST') {
    return 'vehicle-fast'
  }
  if (props.vehicle.mode === 'SLOW') {
    return 'vehicle-slow'
  }
  return 'vehicle-unknown'
})

const chargedKwh = computed(() => props.vehicle.chargedKwh || '0.00')
const requestKwh = computed(() => props.vehicle.requestKwh || '0.00')
</script>

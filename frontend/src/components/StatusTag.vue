<template>
  <el-tag :type="type" size="small">{{ value || '-' }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: {
    type: String,
    default: ''
  }
})

const typeMap = {
  IDLE: 'success',
  WORKING: 'warning',
  FAULT: 'danger',
  OFFLINE: 'info',
  WAITING_AREA: 'info',
  PILE_QUEUE: 'warning',
  CHARGING: 'success',
  FINISHED: 'success',
  INTERRUPTED: 'danger'
}

const type = computed(() => typeMap[props.value] || 'info')
</script>

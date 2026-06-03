<template>
  <el-tag :type="type" size="small">{{ label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { statusLabel, statusTagType } from '../utils/display'

const props = defineProps({
  value: {
    type: String,
    default: ''
  }
})

const label = computed(() => statusLabel(props.value))
const type = computed(() => statusTagType(props.value))
</script>

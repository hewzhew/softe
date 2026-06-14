<template>
  <el-card class="runtime-mode-card" shadow="never">
    <div class="runtime-mode-copy">
      <p class="eyebrow">站点运行台</p>
      <h2>实时与推演</h2>
      <span>{{ description }}</span>
    </div>

    <el-segmented
      :model-value="modelValue"
      :options="modeOptions"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { RUNTIME_MODES } from '../../utils/runtimeMode'

const props = defineProps({
  modelValue: { type: String, default: RUNTIME_MODES.LIVE }
})

defineEmits(['update:modelValue'])

const modeOptions = [
  { label: '实时', value: RUNTIME_MODES.LIVE },
  { label: '推演', value: RUNTIME_MODES.SIMULATION }
]

const description = computed(() => (
  props.modelValue === RUNTIME_MODES.SIMULATION
    ? '使用课程事件序列或临时分支观察非当前时刻的站点状态'
    : '跟随当前业务数据，一秒一秒观察站点运行'
))
</script>

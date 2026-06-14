<template>
  <el-card class="simulation-clock-bar" shadow="never">
    <div class="clock-display">
      <span>模拟时间</span>
      <strong>{{ currentTime || '--:--' }}</strong>
    </div>

    <div class="clock-actions">
      <el-button :disabled="!canStepBack" @click="$emit('step-back')">上一步</el-button>
      <el-button
        type="primary"
        :disabled="!playing && !canPlay"
        @click="$emit(playing ? 'pause' : 'play')"
      >
        {{ playing ? '暂停' : '播放' }}
      </el-button>
      <el-button :disabled="!canStepForward" @click="$emit('step-forward')">下一步</el-button>
      <el-button :disabled="!canReset" @click="$emit('reset')">重置</el-button>
    </div>

    <div class="speed-control">
      <span>速度</span>
      <el-segmented
        :model-value="speed"
        :options="speedOptions"
        :disabled="!canPlay && !playing"
        @update:model-value="$emit('speed', Number($event))"
      />
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  currentTime: { type: String, default: '' },
  playing: { type: Boolean, default: false },
  canPlay: { type: Boolean, default: false },
  canStepBack: { type: Boolean, default: false },
  canStepForward: { type: Boolean, default: false },
  canReset: { type: Boolean, default: false },
  speed: { type: Number, default: 1 }
})

defineEmits(['play', 'pause', 'step-back', 'step-forward', 'reset', 'speed'])

const speedOptions = [
  { label: '0.5x', value: 0.5 },
  { label: '1x', value: 1 },
  { label: '2x', value: 2 },
  { label: '5x', value: 5 },
  { label: '10x', value: 10 }
]
</script>

<style scoped>
.simulation-clock-bar {
  margin-top: 16px;
}

.simulation-clock-bar :deep(.el-card__body) {
  display: grid;
  grid-template-columns: minmax(160px, 0.6fr) minmax(320px, 1.4fr) minmax(260px, 0.9fr);
  gap: 16px;
  align-items: center;
}

.clock-display span,
.speed-control span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.clock-display strong {
  display: block;
  margin-top: 4px;
  color: #172033;
  font-size: 28px;
  font-weight: 700;
}

.clock-actions,
.speed-control {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.speed-control {
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .simulation-clock-bar :deep(.el-card__body) {
    grid-template-columns: 1fr;
  }

  .speed-control {
    justify-content: flex-start;
  }
}
</style>

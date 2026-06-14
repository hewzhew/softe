<template>
  <el-card shadow="never">
    <div class="scenario-loader">
      <div class="scenario-summary">
        <p class="eyebrow">事件来源</p>
        <h2>{{ scenario?.name || '课程事件序列' }}</h2>
        <span>{{ timeRange }}</span>
      </div>

      <div class="scenario-metrics">
        <div>
          <span>事件数量</span>
          <strong>{{ commandCount }}</strong>
        </div>
        <div>
          <span>快照数量</span>
          <strong>{{ snapshotCount }}</strong>
        </div>
      </div>

      <div class="scenario-actions">
        <el-button type="primary" :loading="loading" @click="$emit('load')">
          加载课程事件序列
        </el-button>
        <el-button :disabled="!loaded || commandCount === 0" @click="$emit('copy')">
          复制结果
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false },
  loaded: { type: Boolean, default: false },
  scenario: { type: Object, default: null },
  commandCount: { type: Number, default: 0 },
  snapshotCount: { type: Number, default: 0 }
})

defineEmits(['load', 'copy'])

const timeRange = computed(() => {
  if (!props.loaded || !props.scenario) {
    return '尚未加载'
  }
  return `${props.scenario.startTime || '--:--'} - ${props.scenario.stopTime || '--:--'}`
})
</script>

<style scoped>
.scenario-loader {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(220px, 0.8fr) auto;
  gap: 16px;
  align-items: center;
}

.scenario-summary h2 {
  margin: 2px 0 4px;
  color: #172033;
  font-size: 20px;
  font-weight: 650;
}

.scenario-summary span {
  color: #64748b;
  font-size: 13px;
}

.scenario-metrics,
.scenario-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.scenario-metrics {
  justify-content: center;
}

.scenario-metrics div {
  min-width: 96px;
  padding: 10px 12px;
  border: 1px solid #e4eaf1;
  border-radius: 6px;
  background: #f8fafc;
}

.scenario-metrics span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.scenario-metrics strong {
  display: block;
  margin-top: 4px;
  color: #172033;
  font-size: 20px;
}

.scenario-actions {
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .scenario-loader {
    grid-template-columns: 1fr;
  }

  .scenario-metrics,
  .scenario-actions {
    justify-content: flex-start;
  }
}
</style>

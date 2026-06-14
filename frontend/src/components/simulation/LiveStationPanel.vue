<template>
  <div class="live-station-panel">
    <el-card shadow="never">
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">实时站点</p>
          <h2>当前业务状态</h2>
          <span v-if="updatedAt" class="runtime-status">更新于 {{ updatedAt }}</span>
        </div>
        <div class="live-actions">
          <el-button type="success" :loading="dispatching" @click="$emit('dispatch')">执行调度</el-button>
          <el-button type="primary" :loading="loading" @click="$emit('refresh')">刷新</el-button>
        </div>
      </div>

      <el-alert
        v-if="error"
        class="runtime-alert"
        type="error"
        :title="error"
        show-icon
        :closable="false"
      />

      <div class="runtime-metrics">
        <div>
          <span>等候车辆</span>
          <strong>{{ metricValue('waitingCount') }}</strong>
        </div>
        <div>
          <span>桩内队列</span>
          <strong>{{ metricValue('pileQueueCount') }}</strong>
        </div>
        <div>
          <span>故障桩</span>
          <strong>{{ metricValue('faultPileCount') }}</strong>
        </div>
        <div>
          <span>可服务桩</span>
          <strong>{{ metricValue('activePileCount') }}</strong>
        </div>
      </div>
    </el-card>

    <StationMap :snapshot="snapshot" mode="LIVE" />
  </div>
</template>

<script setup>
import StationMap from './StationMap.vue'

const props = defineProps({
  snapshot: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  dispatching: { type: Boolean, default: false },
  error: { type: String, default: '' },
  updatedAt: { type: String, default: '' }
})

defineEmits(['refresh', 'dispatch'])

function metricValue(key) {
  if (!props.snapshot) {
    return '--'
  }

  return props.snapshot.metrics?.[key] ?? 0
}
</script>

<style scoped>
.runtime-status {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.runtime-alert {
  margin-bottom: 12px;
}

.live-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
</style>

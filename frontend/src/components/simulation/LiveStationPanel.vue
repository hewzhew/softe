<template>
  <div class="live-station-panel">
    <el-card shadow="never">
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">实时站点</p>
          <h2>当前业务状态</h2>
        </div>
        <el-button type="primary" :loading="loading" @click="$emit('refresh')">刷新</el-button>
      </div>

      <div class="runtime-metrics">
        <div>
          <span>等候车辆</span>
          <strong>{{ snapshot?.metrics?.waitingCount ?? 0 }}</strong>
        </div>
        <div>
          <span>桩内队列</span>
          <strong>{{ snapshot?.metrics?.pileQueueCount ?? 0 }}</strong>
        </div>
        <div>
          <span>故障桩</span>
          <strong>{{ snapshot?.metrics?.faultPileCount ?? 0 }}</strong>
        </div>
        <div>
          <span>可服务桩</span>
          <strong>{{ snapshot?.metrics?.activePileCount ?? 0 }}</strong>
        </div>
      </div>
    </el-card>

    <StationMap :snapshot="snapshot" mode="LIVE" />
  </div>
</template>

<script setup>
import StationMap from './StationMap.vue'

defineProps({
  snapshot: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

defineEmits(['refresh'])
</script>

<template>
  <el-card class="station-map-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">站点状态</p>
        <h2>充电站沙盘</h2>
      </div>
      <el-tag :type="tagType" effect="plain">
        {{ modeLabel }} · {{ snapshot?.time || '--:--' }}
      </el-tag>
    </div>

    <div v-if="!snapshot" class="sandbox-empty">{{ emptyText }}</div>
    <div v-else class="station-map">
      <section class="waiting-area">
        <h3>等候区</h3>
        <div class="waiting-list">
          <VehicleToken
            v-for="carId in waitingArea"
            :key="carId"
            :vehicle="vehicles[carId] || { id: carId }"
          />
          <div v-if="waitingArea.length === 0" class="empty-slot">暂无等候车辆</div>
        </div>
      </section>

      <section class="pile-area">
        <h3>快充区</h3>
        <PileLane
          v-for="pile in fastPiles"
          :key="pile.id"
          :pile="pile"
          :vehicles="vehicles"
        />
      </section>

      <section class="pile-area">
        <h3>慢充区</h3>
        <PileLane
          v-for="pile in slowPiles"
          :key="pile.id"
          :pile="pile"
          :vehicles="vehicles"
        />
      </section>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import PileLane from './PileLane.vue'
import VehicleToken from './VehicleToken.vue'

const props = defineProps({
  snapshot: { type: Object, default: null },
  mode: { type: String, default: 'RUNTIME' }
})

const emptyText = computed(() => (
  props.mode === 'SIMULATION'
    ? '加载事件序列后显示站点状态'
    : '正在读取站点状态'
))
const modeLabel = computed(() => (props.mode === 'SIMULATION' ? '快照' : '运行'))
const tagType = computed(() => (props.mode === 'SIMULATION' ? 'warning' : 'success'))
const station = computed(() => props.snapshot?.station || {})
const vehicles = computed(() => props.snapshot?.vehicles || {})
const waitingArea = computed(() => station.value.waitingArea || [])
const fastPiles = computed(() => station.value.fastPiles || [])
const slowPiles = computed(() => station.value.slowPiles || [])
</script>

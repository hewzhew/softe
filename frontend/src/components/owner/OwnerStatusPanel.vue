<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">当前状态</p>
        <h2>{{ title }}</h2>
      </div>
      <StatusTag v-if="carState?.carState" :value="carState.carState" />
    </div>

    <el-alert class="state-alert" :title="nextOwnerAction(carState || {})" type="info" :closable="false" show-icon />

    <div class="metric-grid owner-state-grid">
      <div class="metric-item">
        <span>排队号</span>
        <strong>{{ carState?.queueNum || '-' }}</strong>
      </div>
      <div class="metric-item">
        <span>前方车辆</span>
        <strong>{{ carState?.carNumberBeforePosition ?? '-' }}</strong>
      </div>
      <div class="metric-item">
        <span>充电桩</span>
        <strong>{{ carState?.assignedPileId || '-' }}</strong>
      </div>
    </div>

    <div class="action-row">
      <el-button @click="$emit('refresh')">刷新状态</el-button>
      <el-button :disabled="!canModifyMode" @click="$emit('modifyMode', 'FAST')">改为快充</el-button>
      <el-button :disabled="!canModifyMode" @click="$emit('modifyMode', 'SLOW')">改为慢充</el-button>
      <el-button type="primary" :disabled="!canStart" @click="$emit('startCharging')">开始充电</el-button>
      <el-button type="success" :disabled="!canEnd" @click="$emit('endCharging')">结束并结算</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '../StatusTag.vue'
import { nextOwnerAction } from '../../utils/display'

const props = defineProps({
  carState: { type: Object, default: null }
})

defineEmits(['refresh', 'modifyMode', 'startCharging', 'endCharging'])

const title = computed(() => (props.carState?.carState ? '本次充电服务' : '暂无进行中的服务'))
const canStart = computed(() =>
  props.carState?.carState === 'PILE_QUEUE'
  && Boolean(props.carState?.assignedPileId)
  && (props.carState?.carNumberBeforePosition ?? 0) === 0
)
const canEnd = computed(() => props.carState?.carState === 'CHARGING')
const canModifyMode = computed(() => ['WAITING_AREA', 'PILE_QUEUE'].includes(props.carState?.carState))
</script>

<template>
  <div class="panel-grid">
    <el-card>
      <h2 class="section-title">系统参数</h2>
      <el-form :model="configForm" label-width="112px">
        <el-form-item label="快充桩数量">
          <el-input-number v-model="configForm.fastPileCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="慢充桩数量">
          <el-input-number v-model="configForm.slowPileCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="等候区容量">
          <el-input-number v-model="configForm.waitingAreaSize" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="桩队列长度">
          <el-input-number v-model="configForm.queueLength" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="快充功率">
          <el-input-number v-model="configForm.fastPower" :min="1" :max="100" :step="5" />
        </el-form-item>
        <el-form-item label="慢充功率">
          <el-input-number v-model="configForm.slowPower" :min="1" :max="100" :step="5" />
        </el-form-item>
      </el-form>
      <div class="tool-row">
        <el-button type="primary" @click="saveConfig">初始化</el-button>
        <el-button @click="seedDemo">生成场景</el-button>
        <el-button @click="dispatch">触发调度</el-button>
        <el-button type="danger" plain @click="resetDemo">重置</el-button>
        <el-button :icon="Refresh" circle @click="refresh" />
      </div>
    </el-card>

    <div class="stack">
      <el-card>
        <h2 class="section-title">充电桩</h2>
        <el-table :data="piles" height="260" border>
          <el-table-column prop="pileId" label="编号" width="96" />
          <el-table-column prop="mode" label="模式" width="90" />
          <el-table-column prop="power" label="功率" width="90" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :value="row.workingState" />
            </template>
          </el-table-column>
          <el-table-column prop="currentCarId" label="当前车辆" min-width="130" />
          <el-table-column prop="totalChargeNum" label="次数" width="80" />
          <el-table-column prop="totalCapacity" label="累计电量" width="110" />
        </el-table>
      </el-card>

      <el-card>
        <h2 class="section-title">队列</h2>
        <el-tabs>
          <el-tab-pane label="等候区">
            <el-table :data="queues.waitingArea" height="240" border>
              <el-table-column prop="queueNum" label="排队号" width="100" />
              <el-table-column prop="carId" label="车辆" min-width="130" />
              <el-table-column prop="mode" label="模式" width="90" />
              <el-table-column prop="requestAmount" label="申请电量" width="110" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <StatusTag :value="row.status" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="充电区队列">
            <el-table :data="queues.pileQueues" height="240" border>
              <el-table-column prop="pileId" label="充电桩" width="100" />
              <el-table-column prop="position" label="位置" width="80" />
              <el-table-column prop="queueNum" label="排队号" width="100" />
              <el-table-column prop="carId" label="车辆" min-width="130" />
              <el-table-column prop="requestAmount" label="申请电量" width="110" />
              <el-table-column prop="waitTime" label="等待小时" width="110" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'

const configForm = reactive({
  fastPileCount: 2,
  slowPileCount: 3,
  waitingAreaSize: 10,
  queueLength: 2,
  fastPower: 30,
  slowPower: 10
})

const piles = ref([])
const queues = reactive({
  waitingArea: [],
  pileQueues: []
})

async function refresh() {
  const [config, pileData, queueData] = await Promise.all([
    api.getConfig(),
    api.getPiles().catch(() => []),
    api.getQueues().catch(() => ({ waitingArea: [], pileQueues: [] }))
  ])
  Object.assign(configForm, config)
  piles.value = pileData
  queues.waitingArea = queueData.waitingArea || []
  queues.pileQueues = queueData.pileQueues || []
}

async function saveConfig() {
  await api.updateConfig({ ...configForm })
  ElMessage.success('已初始化')
  await refresh()
}

async function seedDemo() {
  await api.seedDemo()
  ElMessage.success('场景已生成')
  await refresh()
}

async function dispatch() {
  await api.dispatch()
  ElMessage.success('调度完成')
  await refresh()
}

async function resetDemo() {
  await api.resetDemo()
  ElMessage.success('已重置')
  await refresh()
}

onMounted(refresh)
</script>

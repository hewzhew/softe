<template>
  <div class="panel-grid">
    <div class="stack">
      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">演示控制台</p>
            <h2>系统参数</h2>
          </div>
        </div>
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
        <div class="action-row">
          <el-button type="primary" @click="saveConfig">初始化参数</el-button>
          <el-button type="primary" plain @click="seedDemo">生成演示数据</el-button>
          <el-button type="success" @click="dispatch">执行一次调度</el-button>
          <el-button type="danger" plain @click="resetDemo">清空演示数据</el-button>
          <el-button :icon="Refresh" circle title="刷新" @click="refresh" />
        </div>
      </el-card>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">验收流程</p>
            <h2>场景动作</h2>
          </div>
        </div>
        <el-steps :active="demoStep" finish-status="success" direction="vertical" class="demo-steps">
          <el-step title="初始化参数" />
          <el-step title="生成演示数据" />
          <el-step title="执行一次调度" />
          <el-step title="进入车主端或管理员端验证" />
        </el-steps>
      </el-card>
    </div>

    <div class="stack">
      <div class="kpi-strip">
        <div class="kpi-item">
          <span>充电桩</span>
          <strong>{{ piles.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>等候区</span>
          <strong>{{ queues.waitingArea.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>充电区队列</span>
          <strong>{{ queues.pileQueues.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>故障桩</span>
          <strong>{{ faultPileCount }}</strong>
        </div>
      </div>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">调度结果</p>
            <h2>充电桩</h2>
          </div>
        </div>
        <el-table :data="piles" height="280" border empty-text="暂无充电桩，请先初始化参数">
          <el-table-column prop="pileId" label="编号" width="96" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="功率" width="100">
            <template #default="{ row }">{{ formatKw(row.power) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :value="row.workingState" />
            </template>
          </el-table-column>
          <el-table-column label="当前车辆" min-width="130">
            <template #default="{ row }">{{ row.currentCarId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="totalChargeNum" label="次数" width="80" />
          <el-table-column label="累计电量" width="120">
            <template #default="{ row }">{{ formatKwh(row.totalCapacity) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">排队状态</p>
            <h2>队列</h2>
          </div>
        </div>
        <el-tabs>
          <el-tab-pane label="等候区">
            <el-table :data="queues.waitingArea" height="240" border empty-text="暂无等候车辆">
              <el-table-column prop="queueNum" label="排队号" width="100" />
              <el-table-column prop="carId" label="车辆" min-width="130" />
              <el-table-column label="模式" width="90">
                <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
              </el-table-column>
              <el-table-column label="申请电量" width="120">
                <template #default="{ row }">{{ formatKwh(row.requestAmount) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="130">
                <template #default="{ row }">
                  <StatusTag :value="row.status" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="充电区队列">
            <el-table :data="queues.pileQueues" height="240" border empty-text="暂无充电区排队车辆">
              <el-table-column prop="pileId" label="充电桩" width="100" />
              <el-table-column prop="position" label="桩内位置" width="90" />
              <el-table-column prop="queueNum" label="排队号" width="100" />
              <el-table-column prop="carId" label="车辆" min-width="130" />
              <el-table-column label="申请电量" width="120">
                <template #default="{ row }">{{ formatKwh(row.requestAmount) }}</template>
              </el-table-column>
              <el-table-column label="预计等待" width="120">
                <template #default="{ row }">{{ formatHours(row.waitTime) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'
import { formatHours, formatKw, formatKwh, modeLabel } from '../utils/display'

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

const faultPileCount = computed(() => piles.value.filter((pile) => pile.workingState === 'FAULT').length)
const demoStep = computed(() => {
  if (queues.pileQueues.length > 0) {
    return 3
  }
  if (queues.waitingArea.length > 0) {
    return 2
  }
  if (piles.value.length > 0) {
    return 1
  }
  return 0
})

async function runAction(work, successMessage) {
  try {
    const result = await work()
    if (successMessage) {
      ElMessage.success(successMessage)
    }
    return result
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
    return null
  }
}

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
  const result = await runAction(() => api.updateConfig({ ...configForm }), '系统参数已初始化')
  if (result) {
    await refresh()
  }
}

async function seedDemo() {
  const result = await runAction(() => api.seedDemo(), '演示数据已生成')
  if (result) {
    await refresh()
  }
}

async function dispatch() {
  const result = await runAction(() => api.dispatch(), '调度已执行')
  if (result) {
    await refresh()
  }
}

async function resetDemo() {
  const result = await runAction(() => api.resetDemo(), '演示数据已清空')
  if (result) {
    await refresh()
  }
}

onMounted(refresh)
</script>

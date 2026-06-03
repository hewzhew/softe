<template>
  <div class="stack">
    <div class="kpi-strip">
      <div class="kpi-item">
        <span>可服务充电桩</span>
        <strong>{{ idlePileCount }}</strong>
      </div>
      <div class="kpi-item">
        <span>充电中</span>
        <strong>{{ workingPileCount }}</strong>
      </div>
      <div class="kpi-item danger">
        <span>故障桩</span>
        <strong>{{ faultPileCount }}</strong>
      </div>
      <div class="kpi-item">
        <span>等候区车辆</span>
        <strong>{{ queues.waitingArea.length }}</strong>
      </div>
      <div class="kpi-item">
        <span>充电区排队</span>
        <strong>{{ queues.pileQueues.length }}</strong>
      </div>
    </div>

    <el-card>
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">管理员工作台</p>
          <h2>充电桩运行监控</h2>
        </div>
        <el-button :icon="Refresh" circle title="刷新" @click="refresh" />
      </div>
      <el-table :data="piles" height="360" border empty-text="暂无充电桩，请先在演示控制台初始化">
        <el-table-column prop="pileId" label="编号" width="90" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
        </el-table-column>
        <el-table-column label="额定功率" width="110">
          <template #default="{ row }">{{ formatKw(row.power) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.workingState" />
          </template>
        </el-table-column>
        <el-table-column label="当前车辆" min-width="120">
          <template #default="{ row }">{{ row.currentCarId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalChargeNum" label="累计次数" width="100" />
        <el-table-column label="累计时长" width="120">
          <template #default="{ row }">{{ formatHours(row.totalChargeTime) }}</template>
        </el-table-column>
        <el-table-column label="累计电量" width="130">
          <template #default="{ row }">{{ formatKwh(row.totalCapacity) }}</template>
        </el-table-column>
        <el-table-column label="运行操作" width="340">
          <template #default="{ row }">
            <el-button size="small" @click="powerOn(row.pileId)">上电</el-button>
            <el-button size="small" @click="startPile(row.pileId)">投入运行</el-button>
            <el-button size="small" @click="powerOff(row.pileId)">停止服务</el-button>
            <el-button size="small" type="danger" :disabled="row.workingState === 'FAULT'" @click="openFault(row.pileId)">
              上报故障
            </el-button>
            <el-button size="small" :disabled="row.workingState !== 'FAULT'" @click="recover(row.pileId)">恢复服务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="queue-grid">
      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">等待叫号</p>
            <h2>等候区</h2>
          </div>
        </div>
        <el-table :data="queues.waitingArea" height="260" border empty-text="等候区暂无车辆">
          <el-table-column prop="queueNum" label="排队号" width="100" />
          <el-table-column prop="carId" label="车辆" min-width="120" />
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="申请电量" width="120">
            <template #default="{ row }">{{ formatKwh(row.requestAmount) }}</template>
          </el-table-column>
          <el-table-column label="申请时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.requestTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">已进入充电区</p>
            <h2>充电桩队列</h2>
          </div>
        </div>
        <el-table :data="queues.pileQueues" height="260" border empty-text="充电区暂无排队车辆">
          <el-table-column prop="pileId" label="充电桩" width="100" />
          <el-table-column prop="position" label="桩内位置" width="90" />
          <el-table-column prop="queueNum" label="排队号" width="100" />
          <el-table-column prop="carId" label="车辆" min-width="120" />
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="申请电量" width="120">
            <template #default="{ row }">{{ formatKwh(row.requestAmount) }}</template>
          </el-table-column>
          <el-table-column label="预计等待" width="120">
            <template #default="{ row }">{{ formatHours(row.waitTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="faultDialog.visible" title="上报故障并重调度" width="520px">
      <el-alert
        title="系统会将该充电桩置为故障，并按所选策略重新安排受影响车辆。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form class="dialog-form" label-width="96px">
        <el-form-item label="故障充电桩">
          <el-input v-model="faultDialog.pileId" disabled />
        </el-form-item>
        <el-form-item label="调度策略">
          <el-radio-group v-model="faultDialog.strategy">
            <el-radio-button label="PRIORITY">优先保障中断车辆</el-radio-button>
            <el-radio-button label="TIME_ORDER">按请求时间重排</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert :title="faultStrategyHint" type="info" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="faultDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="submitFault">确认上报并重调度</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'
import { formatDateTime, formatHours, formatKw, formatKwh, modeLabel } from '../utils/display'

const piles = ref([])
const queues = reactive({
  waitingArea: [],
  pileQueues: []
})
const faultDialog = reactive({
  visible: false,
  pileId: '',
  strategy: 'PRIORITY'
})

const idlePileCount = computed(() => piles.value.filter((pile) => pile.workingState === 'IDLE').length)
const workingPileCount = computed(() => piles.value.filter((pile) => pile.workingState === 'WORKING').length)
const faultPileCount = computed(() => piles.value.filter((pile) => pile.workingState === 'FAULT').length)
const faultStrategyHint = computed(() => {
  if (faultDialog.strategy === 'PRIORITY') {
    return '优先处理已经开始充电但被故障打断的车辆，再处理原桩队列和等候区车辆。'
  }
  return '按原请求时间重新排列未开始充电车辆，更接近先来先服务。'
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
  const [pileData, queueData] = await Promise.all([
    api.getPiles().catch(() => []),
    api.getQueues().catch(() => ({ waitingArea: [], pileQueues: [] }))
  ])
  piles.value = pileData
  queues.waitingArea = queueData.waitingArea || []
  queues.pileQueues = queueData.pileQueues || []
}

async function powerOn(pileId) {
  const result = await runAction(() => api.powerOn(pileId), '充电桩已上电')
  if (result) {
    await refresh()
  }
}

async function startPile(pileId) {
  const result = await runAction(() => api.startPile(pileId), '充电桩已投入运行')
  if (result) {
    await refresh()
  }
}

async function powerOff(pileId) {
  const result = await runAction(() => api.powerOff(pileId), '充电桩已停止服务')
  if (result) {
    await refresh()
  }
}

function openFault(pileId) {
  faultDialog.pileId = pileId
  faultDialog.visible = true
}

async function submitFault() {
  const result = await runAction(
    () => api.createFault({ pileId: faultDialog.pileId, strategy: faultDialog.strategy }),
    '故障已上报，重调度完成'
  )
  if (result) {
    faultDialog.visible = false
    await refresh()
  }
}

async function recover(pileId) {
  const result = await runAction(() => api.recoverPile(pileId), '充电桩已恢复服务')
  if (result) {
    await refresh()
  }
}

onMounted(refresh)
</script>

<template>
  <div class="owner-layout">
    <el-card class="control-card">
      <div class="card-heading">
        <div>
          <p class="eyebrow">车主自助终端</p>
          <h2>一次充电服务</h2>
        </div>
        <el-tag effect="plain">{{ modeLabel(ownerForm.mode) }}</el-tag>
      </div>

      <el-steps class="owner-steps" :active="ownerStep" direction="vertical" finish-status="success">
        <el-step title="车辆建档" description="录入车辆与车主信息" />
        <el-step title="提交排队" description="选择快充或慢充并申请电量" />
        <el-step title="入桩确认" description="系统调度后显示充电桩" />
        <el-step title="充电结算" description="结束充电后生成账单详单" />
      </el-steps>

      <el-divider />

      <el-form :model="ownerForm" label-width="100px">
        <p class="form-group-title">车辆建档</p>
        <el-form-item label="车辆编号">
          <el-input v-model="ownerForm.carId" placeholder="如 CAR-1" />
        </el-form-item>
        <el-form-item label="车主姓名">
          <el-input v-model="ownerForm.userName" placeholder="如 Alice" />
        </el-form-item>
        <el-form-item label="电池容量">
          <el-input-number v-model="ownerForm.carCapacity" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="账户密码">
          <el-input v-model="ownerForm.password" show-password />
        </el-form-item>
        <div class="action-row">
          <el-button type="primary" @click="createAccount">注册车辆</el-button>
          <el-button @click="setPassword">设置/更新密码</el-button>
        </div>

        <p class="form-group-title">充电申请</p>
        <el-form-item label="充电模式">
          <el-segmented v-model="ownerForm.mode" :options="modeOptions" />
        </el-form-item>
        <el-form-item label="申请电量">
          <el-input-number v-model="ownerForm.requestAmount" :min="1" :max="200" />
        </el-form-item>
        <div class="action-row">
          <el-button type="primary" plain @click="submitRequest">提交充电请求</el-button>
          <el-button :disabled="!canModifyRequest" @click="modifyAmount">修改申请电量</el-button>
          <el-button :disabled="!canModifyRequest" @click="modifyMode">切换充电模式</el-button>
        </div>

        <p class="form-group-title">入桩与结算</p>
        <el-form-item label="系统分配">
          <el-input :model-value="assignedPileText" disabled />
        </el-form-item>
        <el-form-item label="实际电量">
          <el-input-number v-model="ownerForm.actualAmount" :min="1" :max="200" />
        </el-form-item>
        <div class="action-row">
          <el-button @click="queryState">刷新车辆状态</el-button>
          <el-button type="primary" :disabled="!canStartCharging" @click="startCharging">启动充电</el-button>
          <el-button type="success" :disabled="!canEndCharging" @click="endCharging">结束充电并生成账单</el-button>
          <el-button @click="queryBills">查询今日账单</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="stack">
      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">当前服务进度</p>
            <h2>车辆状态</h2>
          </div>
          <StatusTag v-if="carState.carState" :value="carState.carState" />
        </div>

        <el-alert class="state-alert" :title="nextAction" type="info" :closable="false" show-icon />

        <div class="metric-grid">
          <div class="metric-item">
            <span>排队号</span>
            <strong>{{ carState.queueNum || '-' }}</strong>
          </div>
          <div class="metric-item">
            <span>前方车辆</span>
            <strong>{{ carState.carNumberBeforePosition ?? '-' }}</strong>
          </div>
          <div class="metric-item">
            <span>分配充电桩</span>
            <strong>{{ carState.assignedPileId || '-' }}</strong>
          </div>
          <div class="metric-item">
            <span>申请电量</span>
            <strong>{{ formatKwh(ownerForm.requestAmount) }}</strong>
          </div>
        </div>

        <el-descriptions :column="3" border>
          <el-descriptions-item label="车辆">{{ carState.carId || ownerForm.carId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="充电模式">{{ modeLabel(ownerForm.mode) }}</el-descriptions-item>
          <el-descriptions-item label="请求状态">{{ requestStatusLabel(carState.carState) }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDateTime(carState.requestTime) }}</el-descriptions-item>
          <el-descriptions-item label="启动条件" :span="2">{{ startHint }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">费用结果</p>
            <h2>账单</h2>
          </div>
          <el-button size="small" @click="queryBills">刷新账单</el-button>
        </div>
        <el-table :data="bills" height="280" border empty-text="暂无账单，结束充电后生成" @row-click="loadDetails">
          <el-table-column prop="billId" label="账单号" width="90" />
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ row.date || '-' }}</template>
          </el-table-column>
          <el-table-column prop="pileId" label="充电桩" width="100" />
          <el-table-column label="电量" width="120">
            <template #default="{ row }">{{ formatKwh(row.chargeAmount) }}</template>
          </el-table-column>
          <el-table-column label="充电费" width="110">
            <template #default="{ row }">{{ formatCurrency(row.totalChargeFee) }}</template>
          </el-table-column>
          <el-table-column label="服务费" width="110">
            <template #default="{ row }">{{ formatCurrency(row.totalServiceFee) }}</template>
          </el-table-column>
          <el-table-column label="总费用" width="110">
            <template #default="{ row }">{{ formatCurrency(row.totalFee) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="100">
            <template #default="{ row }">
              <el-button size="small" text @click.stop="loadDetails(row)">查看详单</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <div class="card-heading compact">
          <div>
            <p class="eyebrow">分时计费拆分</p>
            <h2>详单</h2>
          </div>
        </div>
        <el-table :data="details" height="220" border empty-text="点击账单行查看详单">
          <el-table-column prop="detailId" label="详单号" width="90" />
          <el-table-column prop="pileId" label="充电桩" width="100" />
          <el-table-column label="电量" width="120">
            <template #default="{ row }">{{ formatKwh(row.chargeAmount) }}</template>
          </el-table-column>
          <el-table-column label="时长" width="120">
            <template #default="{ row }">{{ formatHours(row.chargeDuration) }}</template>
          </el-table-column>
          <el-table-column label="充电费" width="110">
            <template #default="{ row }">{{ formatCurrency(row.chargeFee) }}</template>
          </el-table-column>
          <el-table-column label="服务费" width="110">
            <template #default="{ row }">{{ formatCurrency(row.serviceFee) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="110">
            <template #default="{ row }">{{ formatCurrency(row.subtotalFee) }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'
import {
  formatCurrency,
  formatDateTime,
  formatHours,
  formatKwh,
  modeLabel,
  nextOwnerAction,
  requestStatusLabel
} from '../utils/display'

const modeOptions = [
  { label: '快充', value: 'FAST' },
  { label: '慢充', value: 'SLOW' }
]

const ownerForm = reactive({
  carId: 'CAR-1',
  userName: 'Alice',
  carCapacity: 80,
  password: '123456',
  mode: 'FAST',
  requestAmount: 30,
  pileId: '',
  actualAmount: 30
})

const carState = reactive({})
const bills = ref([])
const details = ref([])

const ownerStep = computed(() => {
  if (carState.carState === 'FINISHED' || bills.value.length > 0) {
    return 4
  }
  if (carState.carState === 'CHARGING') {
    return 3
  }
  if (carState.carState === 'PILE_QUEUE') {
    return 2
  }
  if (carState.carState === 'WAITING_AREA') {
    return 1
  }
  return 0
})

const canModifyRequest = computed(() => ['WAITING_AREA', 'PILE_QUEUE'].includes(carState.carState))
const canStartCharging = computed(() =>
  carState.carState === 'PILE_QUEUE'
  && Boolean(carState.assignedPileId)
  && (carState.carNumberBeforePosition ?? 0) === 0
)
const canEndCharging = computed(() => carState.carState === 'CHARGING' && Boolean(ownerForm.pileId || carState.assignedPileId))
const assignedPileText = computed(() => carState.assignedPileId || ownerForm.pileId || '等待系统调度')
const nextAction = computed(() => nextOwnerAction(carState))
const startHint = computed(() => {
  if (canStartCharging.value) {
    return '当前车辆位于桩队列首位，可以启动充电'
  }
  if (carState.carState === 'PILE_QUEUE') {
    return '已进入充电区队列，需等待前车结束'
  }
  if (carState.carState === 'WAITING_AREA') {
    return '仍在等候区，需等待调度进入充电区'
  }
  if (carState.carState === 'CHARGING') {
    return '正在充电中'
  }
  return '提交请求并执行调度后显示'
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

async function createAccount() {
  await runAction(() => api.createAccount(ownerForm), '车辆注册完成')
}

async function setPassword() {
  await runAction(() => api.setPassword(ownerForm.carId, { password: ownerForm.password }), '密码已设置')
}

async function submitRequest() {
  const result = await runAction(() => api.submitRequest({
    carId: ownerForm.carId,
    requestAmount: ownerForm.requestAmount,
    mode: ownerForm.mode
  }), '充电请求已提交')
  if (result) {
    ownerForm.actualAmount = ownerForm.requestAmount
    Object.assign(carState, result)
  }
}

async function modifyAmount() {
  const result = await runAction(
    () => api.modifyAmount(ownerForm.carId, { amount: ownerForm.requestAmount }),
    '申请电量已修改'
  )
  if (result) {
    ownerForm.actualAmount = ownerForm.requestAmount
    Object.assign(carState, result)
  }
}

async function modifyMode() {
  const result = await runAction(() => api.modifyMode(ownerForm.carId, { mode: ownerForm.mode }), '充电模式已切换')
  if (result) {
    Object.assign(carState, result)
  }
}

async function queryState() {
  const result = await runAction(() => api.getCarState(ownerForm.carId), '车辆状态已刷新')
  if (result) {
    Object.assign(carState, result)
    ownerForm.pileId = result.assignedPileId || ownerForm.pileId
  }
}

async function startCharging() {
  const pileId = carState.assignedPileId || ownerForm.pileId
  const result = await runAction(() => api.startCharging(ownerForm.carId, { pileId }), '充电已启动')
  if (result !== null) {
    ownerForm.pileId = pileId
    await queryState()
  }
}

async function endCharging() {
  const pileId = carState.assignedPileId || ownerForm.pileId
  const bill = await runAction(() => api.endCharging(ownerForm.carId, {
    pileId,
    actualAmount: ownerForm.actualAmount
  }), '账单已生成')
  if (bill) {
    bills.value = [bill, ...bills.value]
    ownerForm.pileId = pileId
    await queryState()
  }
}

async function queryBills() {
  const result = await runAction(
    () => api.queryBills(ownerForm.carId, new Date().toISOString().slice(0, 10)),
    '账单已刷新'
  )
  if (result) {
    bills.value = result
  }
}

async function loadDetails(row) {
  const result = await runAction(() => api.queryDetails(row.billId), '')
  if (result) {
    details.value = result
  }
}
</script>

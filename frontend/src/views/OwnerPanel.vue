<template>
  <div class="panel-grid">
    <el-card>
      <h2 class="section-title">车主操作</h2>
      <el-form :model="ownerForm" label-width="96px">
        <el-form-item label="车辆编号">
          <el-input v-model="ownerForm.carId" />
        </el-form-item>
        <el-form-item label="车主姓名">
          <el-input v-model="ownerForm.userName" />
        </el-form-item>
        <el-form-item label="电池容量">
          <el-input-number v-model="ownerForm.carCapacity" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="ownerForm.password" show-password />
        </el-form-item>
        <el-form-item label="充电模式">
          <el-segmented v-model="ownerForm.mode" :options="modeOptions" />
        </el-form-item>
        <el-form-item label="申请电量">
          <el-input-number v-model="ownerForm.requestAmount" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="充电桩">
          <el-input v-model="ownerForm.pileId" placeholder="如 F-1" />
        </el-form-item>
        <el-form-item label="实际电量">
          <el-input-number v-model="ownerForm.actualAmount" :min="1" :max="200" />
        </el-form-item>
      </el-form>
      <div class="tool-row">
        <el-button type="primary" @click="createAccount">注册</el-button>
        <el-button @click="setPassword">设密</el-button>
        <el-button @click="submitRequest">申请</el-button>
        <el-button @click="modifyAmount">改电量</el-button>
        <el-button @click="modifyMode">改模式</el-button>
        <el-button @click="queryState">查状态</el-button>
        <el-button @click="startCharging">开始</el-button>
        <el-button type="success" @click="endCharging">结束</el-button>
        <el-button @click="queryBills">账单</el-button>
      </div>
    </el-card>

    <div class="stack">
      <el-card>
        <h2 class="section-title">车辆状态</h2>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="车辆">{{ carState.carId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="排队号">{{ carState.queueNum || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="carState.carState" />
          </el-descriptions-item>
          <el-descriptions-item label="前车数量">{{ carState.carNumberBeforePosition ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="充电桩">{{ carState.assignedPileId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatTime(carState.requestTime) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card>
        <h2 class="section-title">账单</h2>
        <el-table :data="bills" height="320" border @row-click="loadDetails">
          <el-table-column prop="billId" label="账单号" width="90" />
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column prop="pileId" label="充电桩" width="100" />
          <el-table-column prop="chargeAmount" label="电量" width="90" />
          <el-table-column prop="totalChargeFee" label="充电费" width="100" />
          <el-table-column prop="totalServiceFee" label="服务费" width="100" />
          <el-table-column prop="totalFee" label="总费用" width="100" />
        </el-table>
      </el-card>

      <el-card>
        <h2 class="section-title">详单</h2>
        <el-table :data="details" height="220" border>
          <el-table-column prop="detailId" label="详单号" width="90" />
          <el-table-column prop="pileId" label="充电桩" width="100" />
          <el-table-column prop="chargeAmount" label="电量" width="90" />
          <el-table-column prop="chargeDuration" label="时长" width="90" />
          <el-table-column prop="chargeFee" label="充电费" width="100" />
          <el-table-column prop="serviceFee" label="服务费" width="100" />
          <el-table-column prop="subtotalFee" label="小计" width="100" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'

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

async function createAccount() {
  await api.createAccount(ownerForm)
  ElMessage.success('注册完成')
}

async function setPassword() {
  await api.setPassword(ownerForm.carId, { password: ownerForm.password })
  ElMessage.success('密码已设置')
}

async function submitRequest() {
  const result = await api.submitRequest({
    carId: ownerForm.carId,
    requestAmount: ownerForm.requestAmount,
    mode: ownerForm.mode
  })
  Object.assign(carState, result)
  ElMessage.success('申请已提交')
}

async function modifyAmount() {
  const result = await api.modifyAmount(ownerForm.carId, { amount: ownerForm.requestAmount })
  Object.assign(carState, result)
  ElMessage.success('电量已修改')
}

async function modifyMode() {
  const result = await api.modifyMode(ownerForm.carId, { mode: ownerForm.mode })
  Object.assign(carState, result)
  ElMessage.success('模式已修改')
}

async function queryState() {
  const result = await api.getCarState(ownerForm.carId)
  Object.assign(carState, result)
  ownerForm.pileId = result.assignedPileId || ownerForm.pileId
}

async function startCharging() {
  await api.startCharging(ownerForm.carId, { pileId: ownerForm.pileId })
  ElMessage.success('已开始充电')
  await queryState()
}

async function endCharging() {
  const bill = await api.endCharging(ownerForm.carId, {
    pileId: ownerForm.pileId,
    actualAmount: ownerForm.actualAmount
  })
  bills.value = [bill, ...bills.value]
  ElMessage.success('已生成账单')
  await queryState().catch(() => {})
}

async function queryBills() {
  bills.value = await api.queryBills(ownerForm.carId, new Date().toISOString().slice(0, 10))
}

async function loadDetails(row) {
  details.value = await api.queryDetails(row.billId)
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div class="owner-portal">
    <OwnerLoginCard
      v-if="stage === OWNER_STAGES.ANONYMOUS"
      v-model:car-id="ownerForm.carId"
      v-model:password="ownerForm.password"
      @login="login"
      @quick-login="quickLogin"
    />

    <template v-else>
      <section class="owner-hero">
        <div>
          <p class="eyebrow">车主自助</p>
          <h2>{{ owner?.name || ownerForm.userName || ownerForm.carId }}</h2>
          <span>{{ ownerPrimaryAction(stage) }}</span>
        </div>
        <el-button @click="logout">退出</el-button>
      </section>

      <div class="owner-portal-grid">
        <div class="owner-portal-primary">
          <OwnerVehiclePanel
            :owner="owner"
            :vehicle="vehicle"
            :form="ownerForm"
            v-model:car-id="ownerForm.carId"
            v-model:user-name="ownerForm.userName"
            v-model:car-capacity="ownerForm.carCapacity"
            @create-vehicle="createVehicle"
          />

          <OwnerRequestPanel
            v-if="stage === OWNER_STAGES.READY"
            :vehicle="vehicle"
            :form="ownerForm"
            @update:mode="ownerForm.mode = $event"
            @update:request-amount="ownerForm.requestAmount = $event"
            @submit-request="submitRequest"
          />

          <OwnerStatusPanel
            v-if="[OWNER_STAGES.WAITING, OWNER_STAGES.CHARGING, OWNER_STAGES.COMPLETED].includes(stage)"
            :car-state="carState"
            @refresh="queryState"
            @start-charging="startCharging"
            @end-charging="endCharging"
          />
        </div>

        <OwnerBillingPanel
          class="owner-portal-billing"
          :bills="bills"
          :details="details"
          @refresh-bills="queryBills"
          @load-details="loadDetails"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'
import OwnerBillingPanel from '../components/owner/OwnerBillingPanel.vue'
import OwnerLoginCard from '../components/owner/OwnerLoginCard.vue'
import OwnerRequestPanel from '../components/owner/OwnerRequestPanel.vue'
import OwnerStatusPanel from '../components/owner/OwnerStatusPanel.vue'
import OwnerVehiclePanel from '../components/owner/OwnerVehiclePanel.vue'
import { notifyStationChanged, stationEvents } from '../stores/stationEvents'
import { OWNER_STAGES, deriveOwnerStage, ownerPrimaryAction } from '../utils/ownerWorkflow'

const ownerForm = reactive({
  carId: 'CAR-1',
  userName: 'Alice',
  carCapacity: 80,
  password: '123456',
  mode: 'FAST',
  requestAmount: 30,
  actualAmount: 30
})

const owner = ref(null)
const vehicle = ref(null)
const carState = ref(null)
const bills = ref([])
const details = ref([])

const stage = computed(() => deriveOwnerStage({
  owner: owner.value,
  vehicle: vehicle.value,
  carState: carState.value,
  bills: bills.value
}))

async function login() {
  await ensureVehicle({ successMessage: '已进入车辆门户' })
}

async function quickLogin() {
  ownerForm.carId = ownerForm.carId || 'CAR-1'
  ownerForm.userName = ownerForm.userName || 'Alice'
  ownerForm.password = ownerForm.password || '123456'
  ownerForm.carCapacity = ownerForm.carCapacity || 80
  ownerForm.requestAmount = ownerForm.requestAmount || 30
  await ensureVehicle({ successMessage: '已进入 CAR-1' })
}

function logout() {
  owner.value = null
  vehicle.value = null
  clearOwnerState()
}

function clearOwnerState() {
  carState.value = null
  bills.value = []
  details.value = []
}

async function runAction(work, successMessage, options = {}) {
  try {
    const result = await work()
    if (successMessage && !options.silent) {
      ElMessage.success(successMessage)
    }
    return result
  } catch (error) {
    if (!options.silentError && !options.silent) {
      ElMessage.error(error.message || '操作失败')
    }
    return null
  }
}

function alreadyRegistered(error) {
  return /already registered|已注册|已登记/.test(error?.message || '')
}

function enterVehicle(account = null) {
  owner.value = { name: account?.userName || ownerForm.userName || '车主用户' }
  vehicle.value = {
    carId: account?.carId || ownerForm.carId,
    carCapacity: account?.carCapacity || ownerForm.carCapacity
  }
  queryState({ silent: true })
  queryBills({ silent: true })
}

async function updatePasswordSilently() {
  if (ownerForm.password) {
    await runAction(
      () => api.setPassword(ownerForm.carId, { password: ownerForm.password }),
      '',
      { silentError: true, silent: true }
    )
  }
}

async function ensureVehicle(options = {}) {
  if (!ownerForm.carId) {
    ElMessage.warning('请填写车辆编号')
    return null
  }

  try {
    const account = await api.createAccount({
      carId: ownerForm.carId,
      userName: ownerForm.userName || ownerForm.carId,
      carCapacity: ownerForm.carCapacity
    })
    await updatePasswordSilently()
    enterVehicle(account)
    notifyStationChanged('owner-account')
    if (options.successMessage) {
      ElMessage.success(options.successMessage)
    }
    return account
  } catch (error) {
    if (!alreadyRegistered(error)) {
      ElMessage.error(error.message || '车辆进入失败')
      return null
    }

    await updatePasswordSilently()
    enterVehicle()
    const existingMessage = options.existingMessage || options.successMessage
    if (existingMessage) {
      ElMessage.success(existingMessage)
    }
    return vehicle.value
  }
}

async function createVehicle() {
  await ensureVehicle({
    successMessage: '车辆已添加',
    existingMessage: '车辆已登记，已进入车辆门户'
  })
}

async function submitRequest() {
  const result = await runAction(() => api.submitRequest({
    carId: ownerForm.carId,
    requestAmount: ownerForm.requestAmount,
    mode: ownerForm.mode
  }), '申请已提交')
  if (result) {
    ownerForm.actualAmount = ownerForm.requestAmount
    carState.value = result
    notifyStationChanged('owner-request')
    await queryState({ silent: true })
  }
}

async function queryState(options = {}) {
  if (!ownerForm.carId) {
    return
  }
  const result = await runAction(
    () => api.getCarState(ownerForm.carId),
    '状态已刷新',
    { silent: options.silent, silentError: options.silent }
  )
  if (result) {
    carState.value = result
  }
}

async function startCharging() {
  const pileId = carState.value?.assignedPileId
  if (!pileId) {
    return
  }
  const result = await runAction(() => api.startCharging(ownerForm.carId, { pileId }), '充电已开始')
  if (result !== null) {
    notifyStationChanged('owner-start')
    await queryState({ silent: true })
  }
}

async function endCharging() {
  const pileId = carState.value?.assignedPileId
  if (!pileId) {
    return
  }
  const bill = await runAction(() => api.endCharging(ownerForm.carId, {
    pileId,
    actualAmount: ownerForm.actualAmount || ownerForm.requestAmount
  }), '账单已生成')
  if (bill) {
    bills.value = [bill, ...bills.value]
    notifyStationChanged('owner-end')
    await queryState({ silent: true })
    await queryBills({ silent: true })
  }
}

async function currentStationDate() {
  const clock = await runAction(() => api.getStationClock(), '', { silent: true, silentError: true })
  return String(clock?.currentTime || new Date().toISOString()).slice(0, 10)
}

async function queryBills(options = {}) {
  if (!ownerForm.carId) {
    return
  }
  const date = await currentStationDate()
  const result = await runAction(
    () => api.queryBills(ownerForm.carId, date),
    '账单已刷新',
    { silent: options.silent, silentError: options.silent }
  )
  if (result) {
    bills.value = result
  }
}

async function loadDetails(row) {
  const result = await runAction(() => api.queryDetails(row.billId), '', { silent: true, silentError: true })
  if (result) {
    details.value = result
  }
}

watch(() => stationEvents.revision, async () => {
  if (['reset', 'seed'].includes(stationEvents.lastAction)) {
    clearOwnerState()
    return
  }
  if (owner.value && vehicle.value) {
    await queryState({ silent: true })
    await queryBills({ silent: true })
  }
})
</script>

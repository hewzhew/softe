<template>
  <div class="stack">
    <el-card>
      <div class="tool-row space-between">
        <h2 class="section-title">充电桩监控</h2>
        <el-button :icon="Refresh" circle @click="refresh" />
      </div>
      <el-table :data="piles" height="360" border>
        <el-table-column prop="pileId" label="编号" width="90" />
        <el-table-column prop="mode" label="模式" width="90" />
        <el-table-column prop="power" label="功率" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.workingState" />
          </template>
        </el-table-column>
        <el-table-column prop="currentCarId" label="当前车辆" min-width="130" />
        <el-table-column prop="totalChargeNum" label="累计次数" width="100" />
        <el-table-column prop="totalChargeTime" label="累计时长" width="100" />
        <el-table-column prop="totalCapacity" label="累计电量" width="100" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="powerOn(row.pileId)">上电</el-button>
            <el-button size="small" @click="startPile(row.pileId)">运行</el-button>
            <el-button size="small" @click="powerOff(row.pileId)">关闭</el-button>
            <el-button size="small" type="danger" @click="openFault(row.pileId)">故障</el-button>
            <el-button size="small" @click="recover(row.pileId)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <h2 class="section-title">队列状态</h2>
      <el-table :data="queues.pileQueues" height="280" border>
        <el-table-column prop="pileId" label="充电桩" width="100" />
        <el-table-column prop="position" label="位置" width="80" />
        <el-table-column prop="queueNum" label="排队号" width="100" />
        <el-table-column prop="carId" label="车辆" min-width="130" />
        <el-table-column prop="mode" label="模式" width="90" />
        <el-table-column prop="requestAmount" label="申请电量" width="110" />
        <el-table-column prop="waitTime" label="等待小时" width="110" />
      </el-table>
    </el-card>

    <el-dialog v-model="faultDialog.visible" title="故障调度" width="420px">
      <el-form label-width="96px">
        <el-form-item label="充电桩">
          <el-input v-model="faultDialog.pileId" disabled />
        </el-form-item>
        <el-form-item label="调度策略">
          <el-select v-model="faultDialog.strategy">
            <el-option label="优先级调度" value="PRIORITY" />
            <el-option label="时间顺序调度" value="TIME_ORDER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="faultDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="submitFault">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import StatusTag from '../components/StatusTag.vue'

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

async function refresh() {
  const [pileData, queueData] = await Promise.all([
    api.getPiles(),
    api.getQueues().catch(() => ({ waitingArea: [], pileQueues: [] }))
  ])
  piles.value = pileData
  queues.waitingArea = queueData.waitingArea || []
  queues.pileQueues = queueData.pileQueues || []
}

async function powerOn(pileId) {
  await api.powerOn(pileId)
  ElMessage.success('已上电')
  await refresh()
}

async function startPile(pileId) {
  await api.startPile(pileId)
  ElMessage.success('已运行')
  await refresh()
}

async function powerOff(pileId) {
  await api.powerOff(pileId)
  ElMessage.success('已关闭')
  await refresh()
}

function openFault(pileId) {
  faultDialog.pileId = pileId
  faultDialog.visible = true
}

async function submitFault() {
  await api.createFault({ pileId: faultDialog.pileId, strategy: faultDialog.strategy })
  faultDialog.visible = false
  ElMessage.success('故障调度完成')
  await refresh()
}

async function recover(pileId) {
  await api.recoverPile(pileId)
  ElMessage.success('已恢复')
  await refresh()
}

onMounted(refresh)
</script>

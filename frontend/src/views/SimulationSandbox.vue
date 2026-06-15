<template>
  <div class="station-runtime">
    <section class="runtime-command-center">
      <div class="runtime-clock-copy">
        <p class="eyebrow">站点运行台</p>
        <h2>{{ stationTimeLabel }}</h2>
        <span>{{ clockStatus }} · {{ pendingEventSummary }}</span>
      </div>

      <div class="runtime-clock-tools">
        <el-button
          :icon="clock.running ? VideoPause : VideoPlay"
          type="primary"
          @click="toggleClock"
        >
          {{ clock.running ? '暂停时钟' : '启动时钟' }}
        </el-button>
        <el-segmented
          :model-value="clock.rate || 1"
          :options="rateOptions"
          @update:model-value="setClockRate(Number($event))"
        />
        <el-button @click="advanceMinutes(5)">推进 5 分钟</el-button>
        <el-button @click="advanceMinutes(30)">推进 30 分钟</el-button>
        <el-button type="success" :loading="dispatching" @click="dispatchStation">执行调度</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="refreshRuntime()">刷新</el-button>
      </div>

      <div class="runtime-jump-row">
        <el-date-picker
          v-model="clockInput"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="指定站点时间"
        />
        <el-button @click="jumpClock">跳转并暂停</el-button>
      </div>
    </section>

    <div class="station-runtime-layout">
      <main class="station-runtime-main">
        <el-card class="runtime-metrics-card" shadow="never">
          <div class="runtime-metrics">
            <div>
              <span>等候车辆</span>
              <strong>{{ metricValue('waitingCount') }}</strong>
            </div>
            <div>
              <span>桩内队列</span>
              <strong>{{ metricValue('pileQueueCount') }}</strong>
            </div>
            <div>
              <span>故障桩</span>
              <strong>{{ metricValue('faultPileCount') }}</strong>
            </div>
            <div>
              <span>可服务桩</span>
              <strong>{{ metricValue('activePileCount') }}</strong>
            </div>
          </div>
        </el-card>

        <StationMap :snapshot="snapshot" mode="RUNTIME" />
      </main>

      <aside class="station-runtime-side">
        <el-card class="runtime-panel" shadow="never">
          <div class="card-heading compact">
            <div>
              <p class="eyebrow">事件接入</p>
              <h2>录入或同步请求</h2>
            </div>
          </div>

          <el-form :model="manualEvent" label-width="86px">
            <el-form-item label="事件时间">
              <el-date-picker
                v-model="manualEvent.eventTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空使用当前站点时间"
              />
            </el-form-item>
            <el-form-item label="车辆编号">
              <el-input v-model="manualEvent.carId" placeholder="如 CAR-8" />
            </el-form-item>
            <el-form-item label="车主姓名">
              <el-input v-model="manualEvent.ownerName" placeholder="如 Student" />
            </el-form-item>
            <el-form-item label="充电模式">
              <el-segmented v-model="manualEvent.mode" :options="modeOptions" />
            </el-form-item>
            <el-form-item label="申请电量">
              <el-input-number v-model="manualEvent.requestAmount" :min="1" :max="200" />
            </el-form-item>
            <el-form-item label="电池容量">
              <el-input-number v-model="manualEvent.carCapacity" :min="1" :max="240" />
            </el-form-item>
          </el-form>

          <div class="action-row">
            <el-button type="primary" :loading="addingEvent" @click="addManualEvent">加入事件流</el-button>
            <el-button @click="useCurrentClockTime">使用当前时间</el-button>
          </div>

          <el-divider />

          <div class="sync-row">
            <div>
              <strong>预置事件流</strong>
              <span>同步一批未来请求、故障和恢复事件，随后由同一个站点时钟推进。</span>
            </div>
            <el-button :loading="importing" @click="importPresetEvents">同步</el-button>
          </div>
        </el-card>

        <el-card class="runtime-panel" shadow="never">
          <div class="card-heading compact">
            <div>
              <p class="eyebrow">事件流</p>
              <h2>待处理与已生效事件</h2>
            </div>
            <el-tag effect="plain">{{ events.length }} 条</el-tag>
          </div>

          <el-table :data="events" height="420" border empty-text="暂无事件">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatStationDateTime(row.eventTime) }}</template>
            </el-table-column>
            <el-table-column label="事件" min-width="120">
              <template #default="{ row }">{{ runtimeEventTypeLabel(row.eventType) }}</template>
            </el-table-column>
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ runtimeEventSourceLabel(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column prop="targetId" label="对象" width="100" />
            <el-table-column label="电量" width="90">
              <template #default="{ row }">{{ row.amount ? `${row.amount} kWh` : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.applied ? 'success' : 'warning'" effect="plain">
                  {{ runtimeEventAppliedLabel(row.applied) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import StationMap from '../components/simulation/StationMap.vue'
import { notifyStationChanged } from '../stores/stationEvents'
import { executeStationDispatch } from '../utils/stationActions'
import {
  CLOCK_RATES,
  addMinutesToStationTime,
  formatClockStatus,
  formatStationDateTime,
  runtimeEventAppliedLabel,
  runtimeEventSourceLabel,
  runtimeEventTypeLabel,
  shouldPollRuntime
} from '../utils/stationClock'

const loading = ref(false)
const dispatching = ref(false)
const addingEvent = ref(false)
const importing = ref(false)
const snapshot = ref(null)
const events = ref([])
const clock = ref({
  currentTime: '',
  rate: 1,
  running: false,
  windowStart: null,
  windowEnd: null
})
const clockInput = ref('')
const pollTimerId = ref(null)

const manualEvent = reactive({
  eventTime: '',
  carId: 'CAR-8',
  ownerName: 'Student',
  carCapacity: 80,
  mode: 'FAST',
  requestAmount: 30,
  sourceName: '人工录入'
})

const modeOptions = [
  { label: '快充', value: 'FAST' },
  { label: '慢充', value: 'SLOW' }
]

const rateOptions = CLOCK_RATES.map((rate) => ({
  label: `${rate}x`,
  value: rate
}))

const stationTimeLabel = computed(() => formatStationDateTime(clock.value.currentTime))
const clockStatus = computed(() => formatClockStatus(clock.value))
const pendingEvent = computed(() => events.value.find((event) => !event.applied))
const pendingEventSummary = computed(() => {
  if (!pendingEvent.value) {
    return '没有待处理事件'
  }
  return `下一事件 ${formatStationDateTime(pendingEvent.value.eventTime)} ${pendingEvent.value.targetId || ''}`
})

function metricValue(key) {
  return snapshot.value?.metrics?.[key] ?? '--'
}

function normalizeDateTimeInput(value) {
  return String(value || '').replace(' ', 'T').slice(0, 19)
}

async function runAction(work, successMessage, options = {}) {
  try {
    const result = await work()
    if (successMessage && !options.silent) {
      ElMessage.success(successMessage)
    }
    return result
  } catch (error) {
    if (!options.silent) {
      ElMessage.error(error.message || '操作失败')
    }
    return null
  }
}

async function refreshRuntime(options = {}) {
  if (!options.silent) {
    loading.value = true
  }
  try {
    const clockData = await api.getStationClock()
    clock.value = clockData
    if (!options.keepClockInput) {
      clockInput.value = normalizeDateTimeInput(clockData.currentTime)
    }
    const snapshotData = await api.getStationSnapshot()
    const eventRows = await api.getStationEvents()
    snapshot.value = snapshotData
    events.value = eventRows || []
  } catch (error) {
    if (!options.silent) {
      ElMessage.error(error.message || '站点状态加载失败')
    }
  } finally {
    loading.value = false
    updatePolling()
  }
}

async function setClockRate(rate) {
  const result = await runAction(() => api.setStationClock({
    currentTime: clock.value.currentTime,
    rate,
    running: clock.value.running,
    windowStart: clock.value.windowStart,
    windowEnd: clock.value.windowEnd
  }), '')
  if (result) {
    clock.value = result
    await refreshRuntime({ silent: true })
  }
}

async function toggleClock() {
  const result = await runAction(
    () => (clock.value.running ? api.pauseStationClock() : api.playStationClock()),
    clock.value.running ? '站点时钟已暂停' : '站点时钟已启动'
  )
  if (result) {
    clock.value = result
    await refreshRuntime({ silent: true })
  }
}

async function jumpClock() {
  if (!clockInput.value) {
    ElMessage.warning('请选择站点时间')
    return
  }
  const result = await runAction(() => api.setStationClock({
    currentTime: clockInput.value,
    rate: clock.value.rate || 1,
    running: false,
    windowStart: clock.value.windowStart,
    windowEnd: clock.value.windowEnd
  }), '站点时间已调整')
  if (result) {
    clock.value = result
    await refreshRuntime({ silent: true, keepClockInput: true })
  }
}

async function advanceMinutes(minutes) {
  const toTime = addMinutesToStationTime(clock.value.currentTime, minutes)
  if (!toTime) {
    ElMessage.warning('当前站点时间不可用')
    return
  }
  const priorRate = clock.value.rate || 1
  const result = await runAction(() => api.advanceStation({ toTime }), `已推进 ${minutes} 分钟`)
  if (result) {
    const adjustedClock = result.rate === priorRate
      ? result
      : await runAction(() => api.setStationClock({
        currentTime: result.currentTime,
        rate: priorRate,
        running: false,
        windowStart: result.windowStart,
        windowEnd: result.windowEnd
      }), '', { silent: true })
    clock.value = adjustedClock || result
    await refreshRuntime({ silent: true })
    notifyStationChanged('runtime-advance')
  }
}

async function dispatchStation() {
  dispatching.value = true
  try {
    const assignments = await executeStationDispatch({
      api,
      refresh: () => refreshRuntime({ silent: true }),
      notify: notifyStationChanged
    })
    const count = Array.isArray(assignments) ? assignments.length : 0
    ElMessage.success(count > 0 ? `调度完成，${count} 辆车进入充电区` : '当前没有可调度车辆')
  } catch (error) {
    ElMessage.error(error.message || '调度失败')
  } finally {
    dispatching.value = false
  }
}

function useCurrentClockTime() {
  manualEvent.eventTime = normalizeDateTimeInput(clock.value.currentTime)
}

async function addManualEvent() {
  if (!manualEvent.carId) {
    ElMessage.warning('请填写车辆编号')
    return
  }
  addingEvent.value = true
  const payload = {
    eventTime: manualEvent.eventTime || null,
    carId: manualEvent.carId,
    ownerName: manualEvent.ownerName || manualEvent.carId,
    carCapacity: manualEvent.carCapacity,
    mode: manualEvent.mode,
    requestAmount: manualEvent.requestAmount,
    sourceName: manualEvent.sourceName || '人工录入'
  }
  const result = await runAction(() => api.addStationEvent(payload), '事件已加入')
  addingEvent.value = false
  if (result) {
    await refreshRuntime({ silent: true })
    notifyStationChanged('runtime-event')
  }
}

async function importPresetEvents() {
  importing.value = true
  const result = await runAction(() => api.importStationEvents({
    sourceType: 'COURSE_PRESET',
    sourceName: '预置事件流',
    resetBeforeImport: true
  }), '')
  importing.value = false
  if (result) {
    ElMessage.success(`已同步 ${result.eventCount || 0} 条事件`)
    await refreshRuntime({ silent: true })
    notifyStationChanged('runtime-import')
  }
}

function updatePolling() {
  if (shouldPollRuntime(clock.value)) {
    if (pollTimerId.value === null) {
      pollTimerId.value = window.setInterval(() => {
        refreshRuntime({ silent: true })
      }, 1000)
    }
    return
  }
  stopPolling()
}

function stopPolling() {
  if (pollTimerId.value !== null) {
    window.clearInterval(pollTimerId.value)
    pollTimerId.value = null
  }
}

onMounted(() => {
  refreshRuntime()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.station-runtime {
  display: grid;
  gap: 16px;
}

.runtime-command-center {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(420px, 1.4fr);
  gap: 16px;
  align-items: center;
  padding: 18px 20px;
  border: 1px solid #dbe3ec;
  border-radius: 6px;
  background: #fff;
}

.runtime-clock-copy h2 {
  margin: 2px 0 4px;
  color: #172033;
  font-size: 28px;
  line-height: 1.15;
  letter-spacing: 0;
}

.runtime-clock-copy span,
.sync-row span {
  color: #64748b;
  font-size: 13px;
}

.runtime-clock-tools,
.runtime-jump-row,
.sync-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.runtime-jump-row {
  grid-column: 2;
}

.station-runtime-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 520px;
  gap: 16px;
  align-items: start;
}

.station-runtime-main,
.station-runtime-side {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.runtime-metrics-card {
  border-radius: 6px;
}

.runtime-panel {
  border-radius: 6px;
}

.sync-row {
  justify-content: space-between;
  align-items: flex-start;
}

.sync-row div {
  display: grid;
  gap: 4px;
}

@media (max-width: 1180px) {
  .runtime-command-center,
  .station-runtime-layout {
    grid-template-columns: 1fr;
  }

  .runtime-jump-row {
    grid-column: auto;
  }

  .runtime-clock-tools,
  .runtime-jump-row {
    justify-content: flex-start;
  }
}
</style>

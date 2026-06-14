<template>
  <div class="simulation-sandbox">
    <ScenarioLoader
      :loading="loading"
      :loaded="loaded"
      :scenario="scenario"
      :command-count="commandCount"
      :snapshot-count="snapshotCount"
      @load="loadScenario"
      @copy="copyRows"
    />

    <SimulationClockBar
      :current-time="playback.currentTime"
      :playing="playing"
      :can-play="canPlay"
      :can-step-back="canStepBack"
      :can-step-forward="canStepForward"
      :can-reset="canReset"
      :speed="playback.speed"
      @play="play"
      @pause="pause"
      @step-back="stepBack"
      @step-forward="stepForwardAction"
      @reset="reset"
      @speed="setSpeed"
    />

    <StationMap :snapshot="playback.currentSnapshot" />

    <el-card class="sandbox-overview" shadow="never">
      <el-empty v-if="!loaded" description="加载后显示 06:00 到 09:30 的运行回放" />
      <template v-else>
        <div class="kpi-strip">
          <div class="kpi-item">
            <span>当前步骤</span>
            <strong>{{ playback.currentSequence }}/{{ maxSequence }}</strong>
          </div>
          <div class="kpi-item">
            <span>开始时间</span>
            <strong>{{ scenario?.startTime || '--:--' }}</strong>
          </div>
          <div class="kpi-item">
            <span>结束时间</span>
            <strong>{{ scenario?.stopTime || '--:--' }}</strong>
          </div>
          <div class="kpi-item">
            <span>当前命令</span>
            <strong>{{ currentCommandLabel }}</strong>
          </div>
          <div class="kpi-item">
            <span>核对结果</span>
            <strong>{{ passedChecks }}/{{ checkCount }}</strong>
          </div>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'
import ScenarioLoader from '../components/simulation/ScenarioLoader.vue'
import SimulationClockBar from '../components/simulation/SimulationClockBar.vue'
import StationMap from '../components/simulation/StationMap.vue'
import { flattenScenarioRows } from '../utils/acceptanceDisplay'
import {
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  setPlaybackSpeed,
  stepBackward,
  stepForward
} from '../utils/simulationPlayback'

const loading = ref(false)
const playback = ref(createPlaybackState())

const bundle = computed(() => playback.value.bundle)
const scenario = computed(() => bundle.value?.scenario || null)
const loaded = computed(() => Boolean(bundle.value))
const playing = computed(() => playback.value.status === 'playing')
const commandCount = computed(() => bundle.value?.commands?.length || 0)
const snapshotCount = computed(() => bundle.value?.snapshots?.length || 0)
const checkCount = computed(() => bundle.value?.checks?.length || 0)
const maxSequence = computed(() => {
  const sequences = (bundle.value?.snapshots || [])
    .map((snapshot) => snapshot.sequence)
    .filter(Number.isFinite)
  return Math.max(0, ...sequences)
})
const canPlay = computed(() => loaded.value && playback.value.status !== 'completed' && maxSequence.value > 0)
const canStepBack = computed(() => loaded.value && playback.value.currentSequence > 0)
const canStepForward = computed(() => loaded.value && playback.value.currentSequence < maxSequence.value)
const canReset = computed(() => loaded.value)
const passedChecks = computed(() => (bundle.value?.checks || []).filter((check) => check.passed).length)
const currentCommandLabel = computed(() => playback.value.currentCommand?.displayText || '等待下一步')

async function loadScenario() {
  loading.value = true
  try {
    const result = await api.runCourseScenario()
    playback.value = loadReplayBundle(createPlaybackState(), result)
    ElMessage.success('课程事件序列已加载')
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function play() {
  playback.value = playPlayback(playback.value)
}

function pause() {
  playback.value = pausePlayback(playback.value)
}

function stepBack() {
  playback.value = stepBackward(playback.value)
}

function stepForwardAction() {
  playback.value = stepForward(playback.value)
}

function reset() {
  playback.value = resetPlayback(playback.value)
}

function setSpeed(speed) {
  playback.value = setPlaybackSpeed(playback.value, speed)
}

async function copyRows() {
  const rows = flattenScenarioRows(bundle.value?.tableRows || [])
  if (rows.length === 0) {
    ElMessage.warning('暂无可复制结果')
    return
  }

  const header = ['时刻', '事件', '位', '快充1', '快充2', '慢充1', '慢充2', '慢充3', '等候区', '备注']
  const lines = [
    header.join('\t'),
    ...rows.map((row) => [
      row.time,
      row.event,
      row.slot,
      row.fast1,
      row.fast2,
      row.slow1,
      row.slow2,
      row.slow3,
      row.waitingAreaText,
      row.notes
    ].join('\t'))
  ]

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('结果已复制')
  } catch {
    ElMessage.warning('当前浏览器未允许复制')
  }
}
</script>

<style scoped>
.simulation-sandbox {
  display: grid;
  gap: 16px;
}

.sandbox-overview {
  min-height: 150px;
}
</style>

<template>
  <div class="simulation-sandbox">
    <RuntimeModeSwitch v-model="runtimeMode" />

    <LiveStationPanel
      v-if="runtimeMode === RUNTIME_MODES.LIVE"
      :snapshot="liveSnapshot"
      :loading="liveLoading"
      :dispatching="liveDispatching"
      :error="liveError"
      :updated-at="liveUpdatedAt"
      @refresh="refreshLiveSnapshot"
      @dispatch="dispatchLiveStation"
    />

    <template v-else>
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

      <div class="simulation-layout">
        <div class="simulation-main">
          <StationMap :snapshot="playback.currentSnapshot" mode="SIMULATION" />
          <EventTimeline
            :commands="bundle?.commands || []"
            :current-sequence="playback.currentSequence"
            @seek="seek"
          />
          <VerificationPanel
            :checks="bundle?.checks || []"
            :table-rows="bundle?.tableRows || []"
            @copy="copyRows"
          />
        </div>

        <aside class="simulation-side">
          <SimulationBranchPanel
            :session="session"
            :source-summary="sourceSummary"
            :scenario="scenario"
          />
          <PlaybackInspector
            :command="playback.currentCommand"
            :transition="playback.currentTransition"
          />
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'
import EventTimeline from '../components/simulation/EventTimeline.vue'
import LiveStationPanel from '../components/simulation/LiveStationPanel.vue'
import PlaybackInspector from '../components/simulation/PlaybackInspector.vue'
import RuntimeModeSwitch from '../components/simulation/RuntimeModeSwitch.vue'
import VerificationPanel from '../components/simulation/VerificationPanel.vue'
import ScenarioLoader from '../components/simulation/ScenarioLoader.vue'
import SimulationBranchPanel from '../components/simulation/SimulationBranchPanel.vue'
import SimulationClockBar from '../components/simulation/SimulationClockBar.vue'
import StationMap from '../components/simulation/StationMap.vue'
import { flattenScenarioRows } from '../utils/acceptanceDisplay'
import { notifyStationChanged } from '../stores/stationEvents'
import { RUNTIME_MODES } from '../utils/runtimeMode'
import { executeStationDispatch } from '../utils/stationActions'
import {
  advancePlaybackByMs,
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  scalePlaybackElapsedMs,
  seekToSequence,
  setPlaybackSpeed,
  stepBackward,
  stepForward
} from '../utils/simulationPlayback'

const loading = ref(false)
const runtimeMode = ref(RUNTIME_MODES.LIVE)
const liveLoading = ref(false)
const liveDispatching = ref(false)
const liveSnapshot = ref(null)
const liveError = ref('')
const liveUpdatedAt = ref('')
const playback = ref(createPlaybackState())
const playbackTimerId = ref(null)
const lastPlaybackTick = ref(null)

const bundle = computed(() => playback.value.bundle)
const scenario = computed(() => bundle.value?.scenario || null)
const session = computed(() => bundle.value?.session || null)
const sourceSummary = computed(() => bundle.value?.sourceSummary || null)
const loaded = computed(() => Boolean(bundle.value))
const playing = computed(() => playback.value.status === 'playing')
const commandCount = computed(() => bundle.value?.commands?.length || 0)
const snapshotCount = computed(() => bundle.value?.snapshots?.length || 0)
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

async function refreshLiveSnapshot() {
  liveError.value = ''
  liveLoading.value = true
  try {
    const snapshot = await api.getStationSnapshot()
    liveSnapshot.value = snapshot
    liveUpdatedAt.value = snapshot?.time || new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (error) {
    const message = error.message || '实时状态加载失败'
    liveError.value = message
    liveSnapshot.value = null
    liveUpdatedAt.value = ''
    ElMessage.error(message)
  } finally {
    liveLoading.value = false
  }
}

async function dispatchLiveStation() {
  liveError.value = ''
  liveDispatching.value = true
  try {
    const assignments = await executeStationDispatch({
      api,
      refresh: refreshLiveSnapshot,
      notify: notifyStationChanged
    })
    const count = Array.isArray(assignments) ? assignments.length : 0
    ElMessage.success(count > 0 ? `调度完成，${count} 辆车进入充电区` : '当前没有可调度车辆')
  } catch (error) {
    const message = error.message || '调度失败'
    liveError.value = message
    ElMessage.error(message)
  } finally {
    liveDispatching.value = false
  }
}

async function loadScenario() {
  stopPlaybackTimer()
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
  startPlaybackTimer()
}

function pause() {
  stopPlaybackTimer()
  playback.value = pausePlayback(playback.value)
}

function stepBack() {
  stopPlaybackTimer()
  playback.value = stepBackward(playback.value)
}

function stepForwardAction() {
  stopPlaybackTimer()
  playback.value = stepForward(playback.value)
}

function reset() {
  stopPlaybackTimer()
  playback.value = resetPlayback(playback.value)
}

function seek(sequence) {
  stopPlaybackTimer()
  playback.value = seekToSequence(playback.value, sequence)
}

function setSpeed(speed) {
  playback.value = setPlaybackSpeed(playback.value, speed)
}

function startPlaybackTimer() {
  if (playback.value.status !== 'playing' || playbackTimerId.value !== null) {
    return
  }

  lastPlaybackTick.value = performance.now()
  playbackTimerId.value = window.setInterval(() => {
    const now = performance.now()
    const elapsedMs = now - lastPlaybackTick.value
    lastPlaybackTick.value = now
    playback.value = advancePlaybackByMs(playback.value, scalePlaybackElapsedMs(elapsedMs))
    if (playback.value.status === 'completed') {
      stopPlaybackTimer()
    }
  }, 1000)
}

function stopPlaybackTimer() {
  if (playbackTimerId.value === null) {
    return
  }

  window.clearInterval(playbackTimerId.value)
  playbackTimerId.value = null
  lastPlaybackTick.value = null
}

watch(runtimeMode, (mode) => {
  if (mode !== RUNTIME_MODES.LIVE) {
    return
  }

  if (playback.value.status === 'playing') {
    pause()
  }
  refreshLiveSnapshot()
})

onMounted(() => {
  refreshLiveSnapshot()
})

onBeforeUnmount(() => {
  stopPlaybackTimer()
})

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
</style>

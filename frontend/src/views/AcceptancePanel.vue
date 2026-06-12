<template>
  <div class="stack">
    <div class="kpi-strip acceptance-summary">
      <div class="kpi-item">
        <span>快充桩</span>
        <strong>{{ scenario?.config?.fastPileCount ?? '-' }}</strong>
      </div>
      <div class="kpi-item">
        <span>慢充桩</span>
        <strong>{{ scenario?.config?.slowPileCount ?? '-' }}</strong>
      </div>
      <div class="kpi-item">
        <span>桩队列长度</span>
        <strong>{{ scenario?.config?.queueLength ?? '-' }}</strong>
      </div>
      <div class="kpi-item">
        <span>等候区容量</span>
        <strong>{{ scenario?.config?.waitingAreaSize ?? '-' }}</strong>
      </div>
      <div class="kpi-item">
        <span>事件数量</span>
        <strong>{{ scenario?.rows?.length ?? '-' }}</strong>
      </div>
    </div>

    <el-card>
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">老师样例</p>
          <h2>测试用例运行</h2>
        </div>
        <div class="action-row">
          <el-button type="primary" :icon="VideoPlay" :loading="loading" @click="loadScenario">
            运行用例
          </el-button>
          <el-button :icon="CopyDocument" :disabled="!scenario" @click="copyRows">
            复制结果
          </el-button>
        </div>
      </div>

      <el-table :data="scenario?.sampleChecks || []" border empty-text="运行后显示样例检查" class="sample-table">
        <el-table-column prop="time" label="时刻" width="90" />
        <el-table-column prop="column" label="位置" width="110" />
        <el-table-column prop="expected" label="样例值" min-width="220" />
        <el-table-column prop="actual" label="系统值" min-width="220" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="sampleCheckType(row)" effect="plain">{{ sampleCheckLabel(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">06:00-09:30</p>
          <h2>事件结果表</h2>
        </div>
        <el-tag effect="plain">{{ timeRange }}</el-tag>
      </div>

      <el-table
        :data="tableRows"
        border
        height="620"
        empty-text="点击运行用例生成结果"
        class="acceptance-table"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="time" label="时刻" width="82" fixed />
        <el-table-column prop="event" label="事件" width="150" fixed />
        <el-table-column prop="slot" label="位" width="54" />
        <el-table-column prop="fast1" label="快充1" min-width="150" />
        <el-table-column prop="fast2" label="快充2" min-width="150" />
        <el-table-column prop="slow1" label="慢充1" min-width="150" />
        <el-table-column prop="slow2" label="慢充2" min-width="150" />
        <el-table-column prop="slow3" label="慢充3" min-width="150" />
        <el-table-column prop="waitingAreaText" label="等候区" min-width="360" show-overflow-tooltip />
        <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, VideoPlay } from '@element-plus/icons-vue'
import { api } from '../api/chargingApi'
import { flattenScenarioRows, sampleCheckLabel, sampleCheckType } from '../utils/acceptanceDisplay'

const loading = ref(false)
const scenario = ref(null)

const tableRows = computed(() => flattenScenarioRows(scenario.value?.rows || []))
const timeRange = computed(() => {
  const config = scenario.value?.config
  if (!config) {
    return '未运行'
  }
  return `${config.startTime} - ${config.endTime}`
})

async function loadScenario() {
  loading.value = true
  try {
    scenario.value = await api.runAcceptanceScenario()
    ElMessage.success('测试用例已运行')
  } catch (error) {
    ElMessage.error(error.message || '运行失败')
  } finally {
    loading.value = false
  }
}

async function copyRows() {
  const header = ['时刻', '事件', '位', '快充1', '快充2', '慢充1', '慢充2', '慢充3', '等候区', '备注']
  const lines = [
    header.join('\t'),
    ...tableRows.value.map((row) => [
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

function rowClassName({ row }) {
  return row.slot === 1 ? 'acceptance-event-row' : 'acceptance-slot-row'
}

onMounted(loadScenario)
</script>

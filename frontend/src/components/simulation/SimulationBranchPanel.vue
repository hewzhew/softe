<template>
  <el-card shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">推演分支</p>
        <h2>{{ branchName }}</h2>
      </div>
      <el-tag effect="plain">只读</el-tag>
    </div>

    <el-descriptions :column="1" border>
      <el-descriptions-item label="会话模式">{{ session?.mode || 'SIMULATION' }}</el-descriptions-item>
      <el-descriptions-item label="基准时间">{{ session?.baseTime || scenario?.startTime || '--:--' }}</el-descriptions-item>
      <el-descriptions-item label="投影策略">{{ session?.materializationPolicy || 'PRECOMPUTED' }}</el-descriptions-item>
      <el-descriptions-item label="事件来源">{{ sourceName }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { formatSourceSummary } from '../../utils/runtimeMode'

const props = defineProps({
  session: { type: Object, default: null },
  sourceSummary: { type: Object, default: null },
  scenario: { type: Object, default: null }
})

const branchName = computed(() => props.session?.branchId || '课程推演分支')
const sourceName = computed(() => formatSourceSummary(props.sourceSummary))
</script>

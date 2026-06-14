<template>
  <div class="simulation-sandbox">
    <el-card>
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">课程事件序列</p>
          <h2>调度沙盘</h2>
        </div>
        <el-button type="primary" :loading="loading" @click="loadScenario">
          加载课程事件序列
        </el-button>
      </div>
      <el-empty v-if="!bundle" description="加载后显示 06:00 到 09:30 的运行回放" />
      <div v-else class="kpi-strip">
        <div class="kpi-item">
          <span>开始时间</span>
          <strong>{{ bundle.scenario.startTime }}</strong>
        </div>
        <div class="kpi-item">
          <span>结束时间</span>
          <strong>{{ bundle.scenario.stopTime }}</strong>
        </div>
        <div class="kpi-item">
          <span>命令数量</span>
          <strong>{{ bundle.commands.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>快照数量</span>
          <strong>{{ bundle.snapshots.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>核对结果</span>
          <strong>{{ passedChecks }}/{{ bundle.checks.length }}</strong>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'

const loading = ref(false)
const bundle = ref(null)

const passedChecks = computed(() => (bundle.value?.checks || []).filter((check) => check.passed).length)

async function loadScenario() {
  loading.value = true
  try {
    bundle.value = await api.runCourseScenario()
    ElMessage.success('课程事件序列已加载')
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}
</script>

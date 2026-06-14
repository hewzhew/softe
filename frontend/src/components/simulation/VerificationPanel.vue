<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">运行结果</p>
        <h2>结果核对</h2>
      </div>
      <el-button :disabled="!tableRows.length" @click="$emit('copy')">复制结果</el-button>
    </div>

    <el-collapse>
      <el-collapse-item title="关键项核对" name="checks">
        <el-table :data="checks" border empty-text="暂无核对项">
          <el-table-column prop="name" label="检查项" min-width="160" />
          <el-table-column prop="expected" label="预期" min-width="220" />
          <el-table-column prop="actual" label="实际" min-width="220" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'warning'" effect="plain">
                {{ row.passed ? '通过' : '需复核' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>

      <el-collapse-item title="表格结果" name="table">
        <el-table :data="flattenedRows" border height="420" empty-text="暂无表格结果">
          <el-table-column prop="time" label="时刻" width="82" fixed />
          <el-table-column prop="event" label="事件" width="150" fixed />
          <el-table-column prop="slot" label="位" width="54" />
          <el-table-column prop="fast1" label="快充1" min-width="150" />
          <el-table-column prop="fast2" label="快充2" min-width="150" />
          <el-table-column prop="slow1" label="慢充1" min-width="150" />
          <el-table-column prop="slow2" label="慢充2" min-width="150" />
          <el-table-column prop="slow3" label="慢充3" min-width="150" />
          <el-table-column prop="waitingAreaText" label="等候区" min-width="320" show-overflow-tooltip />
          <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { flattenScenarioRows } from '../../utils/acceptanceDisplay'

const props = defineProps({
  checks: { type: Array, default: () => [] },
  tableRows: { type: Array, default: () => [] }
})

defineEmits(['copy'])

const flattenedRows = computed(() => flattenScenarioRows(props.tableRows))
</script>

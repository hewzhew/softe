<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">账单记录</p>
        <h2>历史账单</h2>
      </div>
      <el-button size="small" @click="$emit('refreshBills')">刷新</el-button>
    </div>

    <el-table :data="bills" height="260" border empty-text="暂无账单">
      <el-table-column prop="billId" label="账单号" width="90" />
      <el-table-column prop="date" label="日期" width="120" />
      <el-table-column prop="pileId" label="充电桩" width="100" />
      <el-table-column label="电量" width="120">
        <template #default="{ row }">{{ formatKwh(row.chargeAmount) }}</template>
      </el-table-column>
      <el-table-column label="总费用" width="120">
        <template #default="{ row }">{{ formatCurrency(row.totalFee) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="110">
        <template #default="{ row }">
          <el-button size="small" text @click="$emit('loadDetails', row)">查看详单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table class="owner-detail-table" :data="details" height="220" border empty-text="选择账单后显示详单">
      <el-table-column prop="detailId" label="详单号" width="90" />
      <el-table-column prop="pileId" label="充电桩" width="100" />
      <el-table-column label="电量" width="120">
        <template #default="{ row }">{{ formatKwh(row.chargeAmount) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="120">
        <template #default="{ row }">{{ formatHours(row.chargeDuration) }}</template>
      </el-table-column>
      <el-table-column label="小计" width="120">
        <template #default="{ row }">{{ formatCurrency(row.subtotalFee) }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { formatCurrency, formatHours, formatKwh } from '../../utils/display'

defineProps({
  bills: { type: Array, default: () => [] },
  details: { type: Array, default: () => [] }
})

defineEmits(['refreshBills', 'loadDetails'])
</script>

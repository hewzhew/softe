<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">我的车辆</p>
        <h2>{{ vehicle?.carId || '尚未登记车辆' }}</h2>
      </div>
      <el-tag v-if="vehicle" effect="plain">{{ formatKwh(vehicle.carCapacity) }}</el-tag>
    </div>

    <el-form v-if="!vehicle" label-width="92px">
      <el-form-item label="车辆编号">
        <el-input
          :model-value="form.carId"
          placeholder="如 CAR-1"
          @update:model-value="$emit('update:carId', $event)"
        />
      </el-form-item>
      <el-form-item label="车主姓名">
        <el-input
          :model-value="form.userName"
          placeholder="如 Alice"
          @update:model-value="$emit('update:userName', $event)"
        />
      </el-form-item>
      <el-form-item label="电池容量">
        <el-input-number
          :model-value="form.carCapacity"
          :min="1"
          :max="200"
          @update:model-value="$emit('update:carCapacity', $event)"
        />
      </el-form-item>
      <el-button type="primary" @click="$emit('createVehicle')">添加车辆</el-button>
    </el-form>

    <el-descriptions v-else :column="3" border>
      <el-descriptions-item label="车辆">{{ vehicle.carId }}</el-descriptions-item>
      <el-descriptions-item label="车主">{{ owner?.name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="容量">{{ formatKwh(vehicle.carCapacity) }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { formatKwh } from '../../utils/display'

defineProps({
  owner: { type: Object, default: null },
  vehicle: { type: Object, default: null },
  form: { type: Object, required: true }
})

defineEmits(['update:carId', 'update:userName', 'update:carCapacity', 'createVehicle'])
</script>

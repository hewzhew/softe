<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">充电申请</p>
        <h2>发起一次充电</h2>
      </div>
    </div>

    <el-form label-width="92px">
      <el-form-item label="车辆">
        <el-input :model-value="vehicle?.carId || '-'" disabled />
      </el-form-item>
      <el-form-item label="充电模式">
        <el-segmented
          :model-value="form.mode"
          :options="modeOptions"
          @update:model-value="$emit('update:mode', $event)"
        />
      </el-form-item>
      <el-form-item label="申请电量">
        <el-input-number
          :model-value="form.requestAmount"
          :min="1"
          :max="vehicle?.carCapacity || 200"
          @update:model-value="$emit('update:requestAmount', $event)"
        />
      </el-form-item>
      <div class="action-row request-actions">
        <el-button type="primary" :disabled="!vehicle" @click="$emit('submitRequest')">提交申请</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
defineProps({
  vehicle: { type: Object, default: null },
  form: { type: Object, required: true }
})

defineEmits(['update:mode', 'update:requestAmount', 'submitRequest'])

const modeOptions = [
  { label: '快充', value: 'FAST' },
  { label: '慢充', value: 'SLOW' }
]
</script>

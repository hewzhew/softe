<template>
  <el-card class="owner-portal-card owner-login-card" shadow="never">
    <div class="card-heading">
      <div>
        <p class="eyebrow">账户入口</p>
        <h2>车主自助门户</h2>
      </div>
    </div>

    <el-form label-width="82px">
      <el-form-item label="车辆编号">
        <el-input
          :model-value="carId"
          placeholder="如 CAR-1"
          @update:model-value="$emit('update:carId', $event)"
        />
      </el-form-item>
      <el-form-item label="密码">
        <el-input
          :model-value="password"
          show-password
          @update:model-value="$emit('update:password', $event)"
        />
      </el-form-item>
      <div class="action-row">
        <el-button type="primary" @click="$emit('login')">进入车辆</el-button>
        <el-button @click="$emit('quickLogin')">使用 CAR-1</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
defineProps({
  carId: { type: String, default: '' },
  password: { type: String, default: '' }
})

defineEmits(['update:carId', 'update:password', 'login', 'quickLogin'])
</script>

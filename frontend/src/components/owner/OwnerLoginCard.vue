<template>
  <el-card class="owner-portal-card owner-login-card" shadow="never">
    <div class="card-heading">
      <div>
        <p class="eyebrow">账户入口</p>
        <h2>车主自助门户</h2>
      </div>
    </div>

    <el-tabs v-model="activeTab" stretch>
      <el-tab-pane label="登录车辆" name="login">
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
            <el-button type="primary" @click="$emit('login')">登录车辆</el-button>
            <el-button @click="$emit('quickLogin')">使用 CAR-1</el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="注册车辆" name="register">
        <el-form label-width="82px">
          <el-form-item label="车辆编号">
            <el-input
              :model-value="carId"
              placeholder="如 CAR-NEW-1"
              @update:model-value="$emit('update:carId', $event)"
            />
          </el-form-item>
          <el-form-item label="车主姓名">
            <el-input
              :model-value="userName"
              placeholder="如 Student"
              @update:model-value="$emit('update:userName', $event)"
            />
          </el-form-item>
          <el-form-item label="电池容量">
            <el-input-number
              :model-value="carCapacity"
              :min="1"
              :max="240"
              @update:model-value="$emit('update:carCapacity', $event)"
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
            <el-button type="primary" @click="$emit('register')">注册并进入</el-button>
          </div>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  carId: { type: String, default: '' },
  userName: { type: String, default: '' },
  carCapacity: { type: Number, default: 80 },
  password: { type: String, default: '' }
})

defineEmits([
  'update:carId',
  'update:userName',
  'update:carCapacity',
  'update:password',
  'login',
  'quickLogin',
  'register'
])

const activeTab = ref('login')
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <p>波普特大学充电站</p>
        <h1>调度计费系统</h1>
      </div>

      <el-form class="login-form" label-position="top" @submit.prevent="submitLogin">
        <el-form-item label="账号">
          <el-input v-model="loginName" placeholder="如 admin 或 CAR-1" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
        <el-button class="login-button" type="primary" native-type="submit" :loading="loading">
          登录
        </el-button>
      </el-form>

      <div class="login-hints">
        <span>管理员：admin / 123456</span>
        <span>车主：车辆编号 / 已设置密码</span>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '../api/chargingApi'
import { defaultRouteForSession, saveAuthSession } from '../utils/authSession'

const emit = defineEmits(['authenticated'])

const loginName = ref('admin')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

async function submitLogin() {
  error.value = ''
  if (!loginName.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    const session = await api.login({
      loginName: loginName.value.trim(),
      password: password.value
    })
    saveAuthSession(session)
    emit('authenticated', {
      session,
      route: defaultRouteForSession(session)
    })
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

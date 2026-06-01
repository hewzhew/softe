<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div>
        <h1>智能充电桩调度计费系统</h1>
        <p>第 2 组课程验收演示版</p>
      </div>
      <el-tag type="success" effect="dark">Spring Boot + Vue + H2</el-tag>
    </el-header>

    <el-main>
      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="演示控制台" name="demo">
          <DemoConsole />
        </el-tab-pane>
        <el-tab-pane label="车主端" name="owner">
          <OwnerPanel />
        </el-tab-pane>
        <el-tab-pane label="管理员端" name="admin">
          <AdminPanel />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import DemoConsole from './views/DemoConsole.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'

const activeTab = ref('demo')
</script>

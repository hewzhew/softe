<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div>
        <h1>波普特大学充电站</h1>
        <p>充电调度 · 分时计费 · 设备监控</p>
      </div>
      <el-tag type="success" effect="dark">站点运行中</el-tag>
    </el-header>

    <el-main>
      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="调度控制台" name="demo">
          <DemoConsole />
        </el-tab-pane>
        <el-tab-pane label="车主自助" name="owner">
          <OwnerPanel />
        </el-tab-pane>
        <el-tab-pane label="运营管理" name="admin">
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

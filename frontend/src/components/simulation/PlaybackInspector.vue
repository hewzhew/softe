<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">当前步骤</p>
        <h2>{{ command?.displayText || '等待加载' }}</h2>
      </div>
      <el-tag v-if="command" effect="plain">{{ command.time }}</el-tag>
    </div>

    <el-descriptions v-if="command" :column="1" border>
      <el-descriptions-item label="命令">{{ command.type }}</el-descriptions-item>
      <el-descriptions-item label="对象">{{ command.targetId }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ command.sourceText }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="选择事件后显示规则说明" />

    <div v-if="transition?.changes?.length" class="change-list">
      <h3>状态变化</h3>
      <div
        v-for="change in transition.changes"
        :key="`${change.entityType}-${change.entityId}-${change.changeType}`"
        class="change-item"
      >
        <strong>{{ change.entityId }}</strong>
        <span>{{ change.reason }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  command: { type: Object, default: null },
  transition: { type: Object, default: null }
})
</script>

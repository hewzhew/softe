<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">时间轴</p>
        <h2>事件进度</h2>
      </div>
      <el-tag effect="plain">{{ currentSequence }} / {{ commands.length }}</el-tag>
    </div>

    <div class="event-timeline">
      <button
        v-for="command in commands"
        :key="command.sequence"
        type="button"
        class="timeline-point"
        :class="{ active: command.sequence === currentSequence, past: command.sequence < currentSequence }"
        @click="$emit('seek', command.sequence)"
      >
        <span>{{ command.time }}</span>
        <strong>{{ command.targetId }}</strong>
      </button>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  commands: { type: Array, default: () => [] },
  currentSequence: { type: Number, default: 0 }
})

defineEmits(['seek'])
</script>

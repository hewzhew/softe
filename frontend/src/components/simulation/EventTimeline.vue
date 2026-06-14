<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">时间轴</p>
        <h2>事件进度</h2>
      </div>
      <el-tag effect="plain">{{ currentSequence }} / {{ commands.length }}</el-tag>
    </div>

    <p v-if="commands.length > visibleCommands.length" class="timeline-window-note">
      当前显示 {{ visibleCommands.length }} / {{ commands.length }} 个事件，播放时自动跟随当前位置
    </p>

    <div class="event-timeline">
      <button
        v-for="command in visibleCommands"
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
import { computed } from 'vue'
import { visibleTimelineCommands } from '../../utils/simulationPlayback'

const props = defineProps({
  commands: { type: Array, default: () => [] },
  currentSequence: { type: Number, default: 0 },
  windowSize: { type: Number, default: 18 }
})

defineEmits(['seek'])

const visibleCommands = computed(() => visibleTimelineCommands(
  props.commands,
  props.currentSequence,
  props.windowSize
))
</script>

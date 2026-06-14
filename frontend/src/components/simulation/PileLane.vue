<template>
  <section class="pile-lane" :class="{ fault: pile.status === 'FAULT' }">
    <header>
      <div>
        <strong>{{ pile.id }}</strong>
        <span>{{ modeLabel }} · {{ pile.power || '--' }} kW</span>
      </div>
      <el-tag :type="pile.status === 'FAULT' ? 'danger' : 'success'" effect="plain">
        {{ pile.status === 'FAULT' ? '故障' : '运行' }}
      </el-tag>
    </header>

    <div class="pile-queue">
      <VehicleToken
        v-for="carId in queue"
        :key="carId"
        :vehicle="vehicles[carId] || { id: carId, mode: pile.mode }"
      />
      <div v-if="queue.length === 0" class="empty-slot">空闲</div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import VehicleToken from './VehicleToken.vue'

const props = defineProps({
  pile: { type: Object, required: true },
  vehicles: { type: Object, default: () => ({}) }
})

const queue = computed(() => props.pile.queue || [])
const modeLabel = computed(() => (props.pile.mode === 'FAST' ? '快充' : '慢充'))
</script>

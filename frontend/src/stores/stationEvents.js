import { reactive } from 'vue'

export const stationEvents = reactive({
  revision: 0,
  lastAction: 'init'
})

export function notifyStationChanged(action = 'refresh') {
  stationEvents.lastAction = action
  stationEvents.revision += 1
}

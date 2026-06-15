export const CLOCK_RATES = [1, 5, 10, 60]

function normalizeRate(rate) {
  return CLOCK_RATES.includes(Number(rate)) ? Number(rate) : 1
}

export function formatClockStatus(clock = {}) {
  const stateLabel = clock?.running ? '运行中' : '已暂停'
  return `${stateLabel} · ${normalizeRate(clock?.rate)}x`
}

export function shouldPollRuntime(clock = {}) {
  return Boolean(clock?.running)
}

export function formatRuntimeEvent(event = {}) {
  const time = String(event.at || event.time || event.timestamp || '').replace('T', ' ').slice(11, 19) || '--:--:--'
  const carId = event.carId || event.vehicleId || '-'
  const type = event.type || event.eventType || event.name || '-'

  return `${time} ${carId} ${type}`
}

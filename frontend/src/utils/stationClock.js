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
  const time = String(event.eventTime || event.at || event.time || event.timestamp || '').replace('T', ' ').slice(11, 19) || '--:--:--'
  const carId = event.targetId || event.carId || event.vehicleId || '-'
  const type = event.type || event.eventType || event.name || '-'

  return `${time} ${carId} ${type}`
}

export function formatStationDateTime(value) {
  const text = String(value || '').replace('T', ' ').slice(0, 19)
  return text || '--'
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

export function addMinutesToStationTime(value, minutes) {
  if (!value || !Number.isFinite(Number(minutes))) {
    return ''
  }
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  date.setMinutes(date.getMinutes() + Number(minutes))
  return [
    date.getFullYear(),
    pad2(date.getMonth() + 1),
    pad2(date.getDate())
  ].join('-') + `T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

const eventTypeLabels = {
  ChargeRequestSubmitted: '提交申请',
  ChargeRequestCancelled: '取消申请',
  RequestedAmountChanged: '修改电量',
  PileFaulted: '充电桩故障',
  PileRecovered: '充电桩恢复',
  ChargingCompleted: '充电完成',
  BillGenerated: '生成账单'
}

const sourceLabels = {
  COURSE_PRESET: '导入序列',
  MANUAL_OPERATION: '人工录入',
  LIVE_MANUAL: '业务操作'
}

export function runtimeEventTypeLabel(type) {
  return eventTypeLabels[type] || type || '-'
}

export function runtimeEventSourceLabel(sourceType) {
  return sourceLabels[sourceType] || sourceType || '-'
}

export function runtimeEventAppliedLabel(applied) {
  return applied ? '已生效' : '待生效'
}

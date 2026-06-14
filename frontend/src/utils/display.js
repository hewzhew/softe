const modeLabels = {
  FAST: '快充',
  SLOW: '慢充'
}

const requestStatusLabels = {
  WAITING_AREA: '等候区排队',
  PILE_QUEUE: '已分配充电桩',
  CHARGING: '充电中',
  FINISHED: '已完成',
  INTERRUPTED: '故障中断'
}

const pileStatusLabels = {
  IDLE: '空闲',
  WORKING: '充电中',
  FAULT: '故障',
  OFFLINE: '离线'
}

const statusTypes = {
  IDLE: 'success',
  WORKING: 'warning',
  FAULT: 'danger',
  OFFLINE: 'info',
  WAITING_AREA: 'info',
  PILE_QUEUE: 'warning',
  CHARGING: 'success',
  FINISHED: 'success',
  INTERRUPTED: 'danger'
}

export function modeLabel(value) {
  return modeLabels[value] || value || '-'
}

export function requestStatusLabel(value) {
  return requestStatusLabels[value] || value || '-'
}

export function pileStatusLabel(value) {
  return pileStatusLabels[value] || value || '-'
}

export function statusTagType(value) {
  return statusTypes[value] || 'info'
}

export function statusLabel(value) {
  if (value in requestStatusLabels) {
    return requestStatusLabel(value)
  }
  if (value in pileStatusLabels) {
    return pileStatusLabel(value)
  }
  return value || '-'
}

export function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

export function formatKwh(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)} kWh` : '-'
}

export function formatKw(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(0)} kW` : '-'
}

export function formatHours(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)} 小时` : '-'
}

export function formatCurrency(value) {
  return Number.isFinite(Number(value)) ? `¥${Number(value).toFixed(2)}` : '-'
}

export function nextOwnerAction(state = {}) {
  if (!state.carState) {
    return '请先注册车辆并提交充电请求'
  }
  if (state.carState === 'WAITING_AREA') {
    const before = state.carNumberBeforePosition ?? 0
    return `已进入等候区，等待运营调度；前方还有 ${before} 辆车`
  }
  if (state.carState === 'PILE_QUEUE') {
    const before = state.carNumberBeforePosition ?? 0
    if (before > 0) {
      return `已分配 ${state.assignedPileId || '充电桩'}，前方还有 ${before} 辆车`
    }
    return `已分配 ${state.assignedPileId || '充电桩'}，可开始充电`
  }
  if (state.carState === 'CHARGING') {
    return `${state.assignedPileId || '充电桩'} 正在充电，可结束并生成账单`
  }
  if (state.carState === 'FINISHED') {
    return '本次充电已完成，可查看账单和详单'
  }
  if (state.carState === 'INTERRUPTED') {
    return '本次充电受故障影响，等待系统重新调度'
  }
  return '请刷新车辆状态'
}

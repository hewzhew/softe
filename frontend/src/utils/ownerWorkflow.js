export const OWNER_STAGES = {
  ANONYMOUS: 'anonymous',
  NO_VEHICLE: 'no_vehicle',
  READY: 'ready',
  WAITING: 'waiting',
  CHARGING: 'charging',
  COMPLETED: 'completed'
}

const primaryActions = {
  [OWNER_STAGES.ANONYMOUS]: '注册车辆',
  [OWNER_STAGES.NO_VEHICLE]: '完善车辆信息',
  [OWNER_STAGES.READY]: '提交充电请求',
  [OWNER_STAGES.WAITING]: '刷新排队状态',
  [OWNER_STAGES.CHARGING]: '结束充电',
  [OWNER_STAGES.COMPLETED]: '查看账单'
}

export function deriveOwnerStage(context = {}) {
  const vehicle = context.vehicle || context.carState || {}
  const bills = Array.isArray(context.bills) ? context.bills : []

  if (vehicle.carState === 'FINISHED' || bills.length > 0) {
    return OWNER_STAGES.COMPLETED
  }
  if (vehicle.carState === 'CHARGING') {
    return OWNER_STAGES.CHARGING
  }
  if (['WAITING_AREA', 'PILE_QUEUE'].includes(vehicle.carState)) {
    return OWNER_STAGES.WAITING
  }
  if (vehicle.carId || vehicle.carState) {
    return OWNER_STAGES.READY
  }
  if (context.owner || context.account) {
    return OWNER_STAGES.NO_VEHICLE
  }
  return OWNER_STAGES.ANONYMOUS
}

export function ownerPrimaryAction(stageOrContext) {
  const stage = Object.values(OWNER_STAGES).includes(stageOrContext)
    ? stageOrContext
    : deriveOwnerStage(stageOrContext)

  return primaryActions[stage] || primaryActions[OWNER_STAGES.ANONYMOUS]
}

export function validateRequestAmount(requestAmount, carCapacity) {
  const amount = Number(requestAmount)
  const capacity = Number(carCapacity)
  if (!Number.isFinite(amount) || amount <= 0) {
    return { valid: false, message: '申请电量必须大于 0' }
  }
  if (Number.isFinite(capacity) && capacity > 0 && amount > capacity) {
    return { valid: false, message: '申请电量不能超过车辆电池容量' }
  }
  return { valid: true, message: '' }
}

import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  formatDateTime,
  formatHours,
  formatKwh,
  modeLabel,
  nextOwnerAction,
  pileStatusLabel,
  requestStatusLabel
} from './display.js'

describe('display helpers', () => {
  it('maps internal enums to Chinese labels', () => {
    assert.equal(modeLabel('FAST'), '快充')
    assert.equal(modeLabel('SLOW'), '慢充')
    assert.equal(requestStatusLabel('WAITING_AREA'), '等候区排队')
    assert.equal(requestStatusLabel('PILE_QUEUE'), '已分配充电桩')
    assert.equal(requestStatusLabel('CHARGING'), '充电中')
    assert.equal(pileStatusLabel('IDLE'), '空闲')
    assert.equal(pileStatusLabel('WORKING'), '充电中')
    assert.equal(pileStatusLabel('FAULT'), '故障')
  })

  it('formats values used in operation tables', () => {
    assert.equal(formatKwh(30), '30.00 kWh')
    assert.equal(formatHours(1.25), '1.25 小时')
    assert.equal(formatDateTime('2026-06-03T15:24:37'), '2026-06-03 15:24:37')
    assert.equal(formatDateTime(null), '-')
  })

  it('explains the next owner action from request state', () => {
    assert.equal(nextOwnerAction({}), '请先注册车辆并提交充电请求')
    assert.equal(
      nextOwnerAction({ carState: 'WAITING_AREA', carNumberBeforePosition: 2 }),
      '等候区排队中，前方还有 2 辆车'
    )
    assert.equal(
      nextOwnerAction({ carState: 'PILE_QUEUE', assignedPileId: 'F-1', carNumberBeforePosition: 0 }),
      '已分配 F-1，可开始充电'
    )
    assert.equal(nextOwnerAction({ carState: 'CHARGING', assignedPileId: 'F-1' }), 'F-1 正在充电，可结束并生成账单')
  })
})

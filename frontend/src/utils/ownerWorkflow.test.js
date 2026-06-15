import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { deriveOwnerStage, ownerPrimaryAction, OWNER_STAGES } from './ownerWorkflow.js'

describe('owner workflow helpers', () => {
  it('derives anonymous and no vehicle stages before a request exists', () => {
    assert.equal(deriveOwnerStage(), OWNER_STAGES.ANONYMOUS)
    assert.equal(deriveOwnerStage({ owner: { carId: 'CAR-1' } }), OWNER_STAGES.NO_VEHICLE)
  })

  it('derives ready, waiting, charging, and completed stages from service state', () => {
    assert.equal(deriveOwnerStage({ vehicle: { carId: 'CAR-1' } }), OWNER_STAGES.READY)
    assert.equal(deriveOwnerStage({ vehicle: { carState: 'WAITING_AREA' } }), OWNER_STAGES.WAITING)
    assert.equal(deriveOwnerStage({ vehicle: { carState: 'PILE_QUEUE' } }), OWNER_STAGES.WAITING)
    assert.equal(deriveOwnerStage({ vehicle: { carState: 'CHARGING' } }), OWNER_STAGES.CHARGING)
    assert.equal(deriveOwnerStage({ vehicle: { carState: 'FINISHED' } }), OWNER_STAGES.COMPLETED)
    assert.equal(deriveOwnerStage({ vehicle: {}, bills: [{ billId: 1 }] }), OWNER_STAGES.COMPLETED)
  })

  it('derives stages from direct backend car state responses', () => {
    assert.equal(deriveOwnerStage({ carId: 'CAR-1', carState: 'WAITING_AREA' }), OWNER_STAGES.WAITING)
    assert.equal(deriveOwnerStage({ carId: 'CAR-1', carState: 'PILE_QUEUE' }), OWNER_STAGES.WAITING)
    assert.equal(deriveOwnerStage({ carId: 'CAR-1', carState: 'CHARGING' }), OWNER_STAGES.CHARGING)
    assert.equal(deriveOwnerStage({ carId: 'CAR-1', carState: 'FINISHED' }), OWNER_STAGES.COMPLETED)
  })

  it('derives stages when vehicle identity and car state are separate', () => {
    assert.equal(
      deriveOwnerStage({
        owner: {},
        vehicle: { carId: 'CAR-1' },
        carState: { carState: 'WAITING_AREA' }
      }),
      OWNER_STAGES.WAITING
    )
    assert.equal(
      deriveOwnerStage({
        owner: {},
        vehicle: { carId: 'CAR-1' },
        carState: { carState: 'CHARGING' }
      }),
      OWNER_STAGES.CHARGING
    )
    assert.equal(
      deriveOwnerStage({
        owner: {},
        vehicle: { carId: 'CAR-1' },
        carState: { carState: 'FINISHED' }
      }),
      OWNER_STAGES.COMPLETED
    )
  })

  it('falls back safely when car state is explicitly null', () => {
    assert.equal(deriveOwnerStage({ carState: null, bills: [] }), OWNER_STAGES.ANONYMOUS)
    assert.equal(deriveOwnerStage({ owner: {}, carState: null, bills: [] }), OWNER_STAGES.NO_VEHICLE)
    assert.equal(
      deriveOwnerStage({
        owner: {},
        vehicle: { carId: 'CAR-1' },
        carState: null,
        bills: []
      }),
      OWNER_STAGES.READY
    )
  })

  it('chooses the primary owner action for each stage', () => {
    assert.equal(ownerPrimaryAction(OWNER_STAGES.ANONYMOUS), '注册车辆')
    assert.equal(ownerPrimaryAction(OWNER_STAGES.NO_VEHICLE), '完善车辆信息')
    assert.equal(ownerPrimaryAction(OWNER_STAGES.READY), '提交充电请求')
    assert.equal(ownerPrimaryAction(OWNER_STAGES.WAITING), '刷新排队状态')
    assert.equal(ownerPrimaryAction(OWNER_STAGES.CHARGING), '结束充电')
    assert.equal(ownerPrimaryAction(OWNER_STAGES.COMPLETED), '查看账单')
  })
})

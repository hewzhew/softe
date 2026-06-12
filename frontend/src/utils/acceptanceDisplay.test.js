import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { flattenScenarioRows, sampleCheckLabel, sampleCheckType } from './acceptanceDisplay.js'

describe('acceptance display helpers', () => {
  it('maps sample check result to compact labels', () => {
    assert.equal(sampleCheckLabel({ matched: true }), '通过')
    assert.equal(sampleCheckType({ matched: true }), 'success')
    assert.equal(sampleCheckLabel({ matched: false }), '需复核')
    assert.equal(sampleCheckType({ matched: false }), 'warning')
  })

  it('flattens pile queue slots into stable table rows', () => {
    const rows = flattenScenarioRows([
      {
        time: '06:00',
        event: '(A,V1,T,40)',
        fast1: ['-', '-', '-'],
        fast2: ['-', '-', '-'],
        slow1: ['(V1,0.00,0.00)', '-', '-'],
        slow2: ['-', '-', '-'],
        slow3: ['-', '-', '-'],
        waitingAreaText: '-',
        notes: ''
      }
    ])

    assert.equal(rows.length, 3)
    assert.deepEqual(rows[0], {
      key: '06:00-0',
      time: '06:00',
      event: '(A,V1,T,40)',
      slot: 1,
      fast1: '-',
      fast2: '-',
      slow1: '(V1,0.00,0.00)',
      slow2: '-',
      slow3: '-',
      waitingAreaText: '-',
      notes: ''
    })
    assert.equal(rows[1].time, '')
    assert.equal(rows[2].event, '')
  })
})

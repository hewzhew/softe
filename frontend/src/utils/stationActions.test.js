import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { executeStationDispatch } from './stationActions.js'

describe('station actions', () => {
  it('executes dispatch then refreshes and notifies station listeners', async () => {
    const calls = []
    const assignments = [{ pileId: 'F-1', queuePosition: 1 }]

    const result = await executeStationDispatch({
      api: {
        dispatch: async () => {
          calls.push('dispatch')
          return assignments
        }
      },
      refresh: async () => {
        calls.push('refresh')
      },
      notify: (action) => {
        calls.push(`notify:${action}`)
      }
    })

    assert.equal(result, assignments)
    assert.deepEqual(calls, ['dispatch', 'refresh', 'notify:dispatch'])
  })
})

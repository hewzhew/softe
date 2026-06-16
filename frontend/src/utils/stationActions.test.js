import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { executeStationDispatch, executeStationDispatchOne } from './stationActions.js'

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

  it('executes one dispatch then refreshes and notifies station listeners', async () => {
    const calls = []
    const assignment = { carId: 'CAR-F-1', pileId: 'F-1', queuePosition: 1 }

    const result = await executeStationDispatchOne({
      api: {
        dispatchOne: async (payload) => {
          calls.push(`dispatch-one:${payload.mode}`)
          return assignment
        }
      },
      payload: { mode: 'FAST' },
      refresh: async () => {
        calls.push('refresh')
      },
      notify: (action) => {
        calls.push(`notify:${action}`)
      }
    })

    assert.equal(result, assignment)
    assert.deepEqual(calls, ['dispatch-one:FAST', 'refresh', 'notify:dispatch-one'])
  })
})

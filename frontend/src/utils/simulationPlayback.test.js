import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  seekToSequence,
  setPlaybackSpeed,
  stepBackward,
  stepForward
} from './simulationPlayback.js'

const bundle = {
  scenario: { startTime: '06:00', stopTime: '09:30', name: '课程事件序列' },
  commands: [
    { sequence: 1, time: '06:00', displayText: 'V1 提交慢充请求' },
    { sequence: 2, time: '06:05', displayText: 'V2 提交慢充请求' }
  ],
  snapshots: [
    { sequence: 0, time: '06:00', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: {} },
    { sequence: 1, time: '06:00', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' } } },
    { sequence: 2, time: '06:05', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' }, V2: { id: 'V2' } } }
  ],
  transitions: [
    { fromSequence: 0, toSequence: 1, changes: [] },
    { fromSequence: 1, toSequence: 2, changes: [] }
  ],
  checks: [],
  tableRows: []
}

describe('simulation playback helpers', () => {
  it('loads a replay bundle at the initial snapshot', () => {
    const state = loadReplayBundle(createPlaybackState(), bundle)

    assert.equal(state.status, 'loaded')
    assert.equal(state.currentSequence, 0)
    assert.equal(state.currentTime, '06:00')
    assert.equal(state.currentSnapshot.sequence, 0)
  })

  it('steps forward and backward through snapshots', () => {
    let state = loadReplayBundle(createPlaybackState(), bundle)
    state = stepForward(state)
    assert.equal(state.status, 'paused')
    assert.equal(state.currentSequence, 1)
    assert.equal(state.currentCommand.displayText, 'V1 提交慢充请求')

    state = stepForward(state)
    assert.equal(state.currentSequence, 2)
    assert.equal(state.status, 'completed')

    state = stepBackward(state)
    assert.equal(state.status, 'paused')
    assert.equal(state.currentSequence, 1)
  })

  it('supports play pause speed seek and reset', () => {
    let state = loadReplayBundle(createPlaybackState(), bundle)
    state = playPlayback(state)
    assert.equal(state.status, 'playing')

    state = setPlaybackSpeed(state, 5)
    assert.equal(state.speed, 5)

    state = pausePlayback(state)
    assert.equal(state.status, 'paused')

    state = seekToSequence(state, 2)
    assert.equal(state.currentSequence, 2)
    assert.equal(state.status, 'completed')

    state = resetPlayback(state)
    assert.equal(state.status, 'loaded')
    assert.equal(state.currentSequence, 0)
  })
})

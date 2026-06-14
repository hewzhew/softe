import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  advancePlaybackByMs,
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  scalePlaybackElapsedMs,
  seekToSequence,
  setPlaybackSpeed,
  stepBackward,
  stepForward,
  visibleTimelineCommands
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

const timedBundle = {
  scenario: { startTime: '06:00', stopTime: '06:10', name: 'Timed playback' },
  commands: [
    { sequence: 1, time: '06:02', displayText: 'V1 提交慢充请求' },
    { sequence: 2, time: '06:05', displayText: 'V2 提交慢充请求' },
    { sequence: 3, time: '06:09', displayText: 'V3 提交快充请求' }
  ],
  snapshots: [
    { sequence: 0, time: '06:00', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: {} },
    { sequence: 1, time: '06:02', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' } } },
    { sequence: 2, time: '06:05', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' }, V2: { id: 'V2' } } },
    { sequence: 3, time: '06:09', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' }, V2: { id: 'V2' }, V3: { id: 'V3' } } }
  ],
  transitions: [
    { fromSequence: 0, toSequence: 1, changes: [] },
    { fromSequence: 1, toSequence: 2, changes: [] },
    { fromSequence: 2, toSequence: 3, changes: [] }
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

  it('pauses playing playback when stepping forward manually before completion', () => {
    const state = stepForward(playPlayback(loadReplayBundle(createPlaybackState(), timedBundle)))

    assert.equal(state.currentSequence, 1)
    assert.equal(state.status, 'paused')
  })

  it('completes playing playback when stepping forward manually to the final snapshot', () => {
    const loadedAtPenultimateSnapshot = seekToSequence(loadReplayBundle(createPlaybackState(), timedBundle), 2)
    const state = stepForward(playPlayback(loadedAtPenultimateSnapshot))

    assert.equal(state.currentSequence, 3)
    assert.equal(state.status, 'completed')
  })

  it('pauses playing playback when seeking manually before completion', () => {
    const state = seekToSequence(playPlayback(loadReplayBundle(createPlaybackState(), timedBundle)), 2)

    assert.equal(state.currentSequence, 2)
    assert.equal(state.status, 'paused')
  })

  it('normalizes fractional seek targets to valid snapshots', () => {
    const state = seekToSequence(loadReplayBundle(createPlaybackState(), bundle), 0.5)

    assert.equal(state.currentSequence, 0)
    assert.equal(state.currentSnapshot.sequence, 0)
  })

  it('keeps null and empty bundles empty and non-playable', () => {
    assert.deepEqual(loadReplayBundle(createPlaybackState(), null), createPlaybackState())
    assert.deepEqual(loadReplayBundle(createPlaybackState(), { snapshots: [] }), createPlaybackState())

    const state = playPlayback(loadReplayBundle(createPlaybackState(), null))
    assert.equal(state.status, 'empty')
    assert.equal(state.bundle, null)
    assert.equal(state.currentSnapshot, null)
  })

  it('ignores invalid playback speeds', () => {
    const state = setPlaybackSpeed(loadReplayBundle(createPlaybackState(), bundle), 3)

    assert.equal(state.speed, 1)
  })

  it('scales real elapsed milliseconds for sandbox demo playback', () => {
    assert.equal(scalePlaybackElapsedMs(1000), 60_000)
    assert.equal(scalePlaybackElapsedMs(250, 120), 30_000)
  })

  it('conservatively ignores invalid playback elapsed scaling inputs', () => {
    assert.equal(scalePlaybackElapsedMs(Number.NaN), 0)
    assert.equal(scalePlaybackElapsedMs(-1000), 0)
    assert.equal(scalePlaybackElapsedMs(1000, 0), 0)
    assert.equal(scalePlaybackElapsedMs(1000, Number.NaN), 0)
  })

  it('advances playing playback by elapsed milliseconds and speed', () => {
    let state = playPlayback(loadReplayBundle(createPlaybackState(), timedBundle))

    state = advancePlaybackByMs(state, 60_000)
    assert.equal(state.currentTime, '06:01')
    assert.equal(state.currentSequence, 0)
    assert.equal(state.status, 'playing')

    state = advancePlaybackByMs(setPlaybackSpeed(state, 2), 120_000)
    assert.equal(state.currentTime, '06:05')
    assert.equal(state.currentSequence, 2)
    assert.equal(state.currentCommand.displayText, 'V2 提交慢充请求')
    assert.equal(state.status, 'playing')
  })

  it('does not advance non-playing playback null bundles or invalid elapsed values', () => {
    const loaded = loadReplayBundle(createPlaybackState(), timedBundle)

    assert.deepEqual(advancePlaybackByMs(loaded, 60_000), loaded)
    assert.deepEqual(advancePlaybackByMs(playPlayback(createPlaybackState()), 60_000), createPlaybackState())

    const playing = playPlayback(loaded)
    assert.deepEqual(advancePlaybackByMs(playing, Number.NaN), playing)
    assert.deepEqual(advancePlaybackByMs(playing, -1000), playing)
  })

  it('does not advance when the current playback time is invalid', () => {
    const playing = {
      ...playPlayback(loadReplayBundle(createPlaybackState(), timedBundle)),
      currentTime: 'not-a-time',
      currentMinute: null,
      currentSnapshot: null
    }

    assert.deepEqual(advancePlaybackByMs(playing, 60_000), playing)
  })

  it('skips across multiple commands and completes at the final command', () => {
    let state = playPlayback(loadReplayBundle(createPlaybackState(), timedBundle))
    state = setPlaybackSpeed(state, 5)

    state = advancePlaybackByMs(state, 120_000)

    assert.equal(state.currentTime, '06:10')
    assert.equal(state.currentSequence, 3)
    assert.equal(state.currentCommand.displayText, 'V3 提交快充请求')
    assert.equal(state.status, 'completed')
  })

  it('returns a bounded timeline window around the current sequence', () => {
    const commands = Array.from({ length: 30 }, (_, index) => ({
      sequence: index + 1,
      time: `06:${String(index).padStart(2, '0')}`,
      displayText: `event-${index + 1}`
    }))

    const windowed = visibleTimelineCommands(commands, 15, 9)

    assert.equal(windowed.length, 9)
    assert.equal(windowed[0].sequence, 11)
    assert.equal(windowed[8].sequence, 19)
  })

  it('keeps the full timeline when command count is below the window size', () => {
    const commands = [{ sequence: 1 }, { sequence: 2 }]

    assert.deepEqual(visibleTimelineCommands(commands, 1, 9), commands)
  })

  it('falls back to the default timeline window when fractional size truncates below one', () => {
    const commands = Array.from({ length: 20 }, (_, index) => ({
      sequence: index + 1
    }))

    const windowed = visibleTimelineCommands(commands, 10, 0.5)

    assert.equal(windowed.length, 18)
  })
})

import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  RUNTIME_MODES,
  formatSourceSummary,
  getRuntimeModeCapabilities,
  normalizeRuntimeMode
} from './runtimeMode.js'

describe('runtime mode helpers', () => {
  it('normalizes unknown modes to live', () => {
    assert.equal(normalizeRuntimeMode('SIMULATION'), RUNTIME_MODES.SIMULATION)
    assert.equal(normalizeRuntimeMode('bad-mode'), RUNTIME_MODES.LIVE)
    assert.equal(normalizeRuntimeMode(null), RUNTIME_MODES.LIVE)
  })

  it('keeps live mode free of playback controls', () => {
    const live = getRuntimeModeCapabilities(RUNTIME_MODES.LIVE)

    assert.equal(live.canPlay, false)
    assert.equal(live.canUseSpeed, false)
    assert.equal(live.canRefresh, true)
  })

  it('enables playback controls for simulation mode', () => {
    const simulation = getRuntimeModeCapabilities(RUNTIME_MODES.SIMULATION)

    assert.equal(simulation.canPlay, true)
    assert.equal(simulation.canUseSpeed, true)
    assert.equal(simulation.canRefresh, false)
  })

  it('formats source summaries conservatively', () => {
    assert.equal(formatSourceSummary(null), '未选择事件来源')
    assert.equal(formatSourceSummary({ primarySourceName: '课程事件序列' }), '课程事件序列')
    assert.equal(formatSourceSummary({ primarySourceType: 'LIVE_MANUAL' }), 'LIVE_MANUAL')
  })
})

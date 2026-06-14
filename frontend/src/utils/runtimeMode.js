export const RUNTIME_MODES = Object.freeze({
  LIVE: 'LIVE',
  SIMULATION: 'SIMULATION'
})

const MODE_CAPABILITIES = Object.freeze({
  [RUNTIME_MODES.LIVE]: Object.freeze({
    label: '实时',
    canPlay: false,
    canUseSpeed: false,
    canStep: false,
    canSeek: false,
    canRefresh: true
  }),
  [RUNTIME_MODES.SIMULATION]: Object.freeze({
    label: '推演',
    canPlay: true,
    canUseSpeed: true,
    canStep: true,
    canSeek: true,
    canRefresh: false
  })
})

export function normalizeRuntimeMode(mode) {
  return Object.values(RUNTIME_MODES).includes(mode) ? mode : RUNTIME_MODES.LIVE
}

export function getRuntimeModeCapabilities(mode) {
  return MODE_CAPABILITIES[normalizeRuntimeMode(mode)]
}

export function formatSourceSummary(sourceSummary) {
  if (!sourceSummary || typeof sourceSummary !== 'object') {
    return '未选择事件来源'
  }

  return sourceSummary.primarySourceName || sourceSummary.primarySourceType || '未选择事件来源'
}

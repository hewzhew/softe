const SPEEDS = [0.5, 1, 2, 5, 10]

export function createPlaybackState() {
  return {
    status: 'empty',
    bundle: null,
    currentSequence: 0,
    currentTime: '',
    currentMinute: null,
    speed: 1,
    currentSnapshot: null,
    currentCommand: null,
    currentTransition: null
  }
}

export function loadReplayBundle(state, bundle) {
  if (!hasSnapshots(bundle)) {
    return createPlaybackState()
  }

  const initialSnapshot = bundle?.snapshots?.[0] || null
  const maxSequence = maxSnapshotSequence(bundle)

  return derive({
    ...state,
    status: 'loaded',
    bundle,
    currentSequence: normalizeSequence(initialSnapshot?.sequence ?? 0, maxSequence),
    currentTime: initialSnapshot?.time || bundle?.scenario?.startTime || '',
    currentMinute: parseTimeToMinutes(initialSnapshot?.time || bundle?.scenario?.startTime),
    speed: 1
  })
}

export function playPlayback(state) {
  if (!state.bundle || state.status === 'completed') {
    return state
  }

  return { ...state, status: 'playing' }
}

export function pausePlayback(state) {
  if (state.status !== 'playing') {
    return state
  }

  return { ...state, status: 'paused' }
}

export function stepForward(state) {
  if (!state.bundle) {
    return state
  }

  const maxSequence = maxSnapshotSequence(state.bundle)
  const currentSequence = normalizeSequence(state.currentSequence, maxSequence)
  return derive({
    ...state,
    currentSequence: normalizeSequence(currentSequence + 1, maxSequence),
    currentTime: '',
    currentMinute: null,
    status: 'paused'
  })
}

export function stepBackward(state) {
  if (!state.bundle) {
    return state
  }

  const maxSequence = maxSnapshotSequence(state.bundle)
  const currentSequence = normalizeSequence(state.currentSequence, maxSequence)
  return derive({
    ...state,
    currentSequence: normalizeSequence(currentSequence - 1, maxSequence),
    currentTime: '',
    currentMinute: null,
    status: 'paused'
  })
}

export function seekToSequence(state, sequence) {
  if (!state.bundle) {
    return state
  }

  const maxSequence = maxSnapshotSequence(state.bundle)
  return derive({
    ...state,
    currentSequence: normalizeSequence(sequence, maxSequence, state.currentSequence),
    currentTime: '',
    currentMinute: null,
    status: 'paused'
  })
}

export function setPlaybackSpeed(state, speed) {
  if (!SPEEDS.includes(speed)) {
    return state
  }

  return { ...state, speed }
}

export function scalePlaybackElapsedMs(elapsedMs, scale = 60) {
  if (!Number.isFinite(elapsedMs) || elapsedMs <= 0 || !Number.isFinite(scale) || scale <= 0) {
    return 0
  }

  return elapsedMs * scale
}

export function visibleTimelineCommands(commands, currentSequence, windowSize = 18) {
  if (!Array.isArray(commands) || commands.length === 0) {
    return []
  }

  const size = Number.isFinite(windowSize) && windowSize > 0
    ? Math.trunc(windowSize)
    : 18

  if (commands.length <= size) {
    return commands
  }

  const currentIndex = Math.max(
    0,
    commands.findIndex((command) => command.sequence === currentSequence)
  )
  const half = Math.floor(size / 2)
  const start = Math.max(0, Math.min(currentIndex - half, commands.length - size))

  return commands.slice(start, start + size)
}

export function advancePlaybackByMs(state, elapsedMs) {
  if (state.status !== 'playing' || !state.bundle || !Number.isFinite(elapsedMs) || elapsedMs <= 0) {
    return state
  }

  const speed = Number.isFinite(state.speed) && state.speed > 0 ? state.speed : 1
  const currentMinute = Number.isFinite(state.currentMinute)
    ? state.currentMinute
    : parseTimeToMinutes(state.currentTime || state.currentSnapshot?.time || state.bundle?.scenario?.startTime)

  if (!Number.isFinite(currentMinute)) {
    return state
  }

  const stopMinute = parseTimeToMinutes(state.bundle?.scenario?.stopTime)
  const targetMinute = clampMinute(currentMinute + (elapsedMs / 60_000) * speed, stopMinute)
  const targetSequence = sequenceAtOrBeforeTime(state.bundle, targetMinute, state.currentSequence)

  return derive({
    ...state,
    currentSequence: targetSequence,
    currentMinute: targetMinute,
    currentTime: formatMinutesAsTime(targetMinute)
  })
}

export function resetPlayback(state) {
  if (!state.bundle) {
    return createPlaybackState()
  }

  return loadReplayBundle(createPlaybackState(), state.bundle)
}

function derive(state) {
  const snapshots = state.bundle?.snapshots || []
  const commands = state.bundle?.commands || []
  const transitions = state.bundle?.transitions || []
  const maxSequence = maxSnapshotSequence(state.bundle)
  const currentSequence = normalizeSequence(state.currentSequence, maxSequence)
  const currentSnapshot = snapshots.find((snapshot) => snapshot.sequence === currentSequence) || null
  const currentCommand = commands.find((command) => command.sequence === currentSequence) || null
  const currentTransition = transitions.find((transition) => transition.toSequence === currentSequence) || null

  return {
    ...state,
    currentSequence,
    status: deriveStatus({ ...state, currentSequence }, maxSequence),
    currentSnapshot,
    currentCommand,
    currentTransition,
    currentTime: state.currentTime || currentSnapshot?.time || '',
    currentMinute: Number.isFinite(state.currentMinute)
      ? state.currentMinute
      : parseTimeToMinutes(currentSnapshot?.time || state.currentTime)
  }
}

function deriveStatus(state, maxSequence) {
  if (state.currentSequence >= maxSequence && maxSequence > 0) {
    return 'completed'
  }

  if (state.status === 'playing') {
    return 'playing'
  }

  if (state.currentSequence === 0) {
    return 'loaded'
  }

  return 'paused'
}

function hasSnapshots(bundle) {
  return Array.isArray(bundle?.snapshots) && bundle.snapshots.length > 0
}

function normalizeSequence(sequence, maxSequence, fallback = 0) {
  const value = Number.isFinite(sequence) ? sequence : fallback
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.max(0, Math.min(Math.trunc(value), maxSequence))
}

function maxSnapshotSequence(bundle) {
  const sequences = (bundle?.snapshots || [])
    .map((snapshot) => snapshot.sequence)
    .filter(Number.isFinite)

  return Math.max(0, ...sequences)
}

function sequenceAtOrBeforeTime(bundle, targetMinute, fallbackSequence) {
  const commands = (bundle?.commands || [])
    .map((command) => ({
      sequence: command.sequence,
      minute: parseTimeToMinutes(command.time)
    }))
    .filter((command) => Number.isFinite(command.sequence) && Number.isFinite(command.minute))
    .sort((a, b) => a.minute - b.minute || a.sequence - b.sequence)

  let sequence = normalizeSequence(fallbackSequence, maxSnapshotSequence(bundle))
  for (const command of commands) {
    if (command.minute <= targetMinute) {
      sequence = command.sequence
    }
  }

  return sequence
}

function clampMinute(minute, stopMinute) {
  if (Number.isFinite(stopMinute)) {
    return Math.min(minute, stopMinute)
  }

  return minute
}

function parseTimeToMinutes(time) {
  if (typeof time !== 'string') {
    return null
  }

  const match = /^(\d{1,2}):(\d{2})$/.exec(time.trim())
  if (!match) {
    return null
  }

  const hours = Number(match[1])
  const minutes = Number(match[2])
  if (!Number.isInteger(hours) || !Number.isInteger(minutes) || minutes < 0 || minutes > 59) {
    return null
  }

  return hours * 60 + minutes
}

function formatMinutesAsTime(minute) {
  const totalMinutes = Math.max(0, Math.floor(minute))
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

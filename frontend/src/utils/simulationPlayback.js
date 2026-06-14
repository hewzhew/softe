const SPEEDS = [0.5, 1, 2, 5, 10]

export function createPlaybackState() {
  return {
    status: 'empty',
    bundle: null,
    currentSequence: 0,
    currentTime: '',
    speed: 1,
    currentSnapshot: null,
    currentCommand: null,
    currentTransition: null
  }
}

export function loadReplayBundle(state, bundle) {
  const initialSnapshot = bundle?.snapshots?.[0] || null

  return derive({
    ...state,
    status: 'loaded',
    bundle,
    currentSequence: initialSnapshot?.sequence ?? 0,
    currentTime: initialSnapshot?.time || bundle?.scenario?.startTime || '',
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
  return derive({
    ...state,
    currentSequence: clampSequence(state.currentSequence + 1, maxSequence)
  })
}

export function stepBackward(state) {
  if (!state.bundle) {
    return state
  }

  return derive({
    ...state,
    currentSequence: clampSequence(state.currentSequence - 1, maxSnapshotSequence(state.bundle)),
    status: 'paused'
  })
}

export function seekToSequence(state, sequence) {
  if (!state.bundle) {
    return state
  }

  return derive({
    ...state,
    currentSequence: clampSequence(sequence, maxSnapshotSequence(state.bundle))
  })
}

export function setPlaybackSpeed(state, speed) {
  if (!SPEEDS.includes(speed)) {
    return state
  }

  return { ...state, speed }
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
  const currentSnapshot = snapshots.find((snapshot) => snapshot.sequence === state.currentSequence) || null
  const currentCommand = commands.find((command) => command.sequence === state.currentSequence) || null
  const currentTransition = transitions.find((transition) => transition.toSequence === state.currentSequence) || null
  const maxSequence = maxSnapshotSequence(state.bundle)

  return {
    ...state,
    status: deriveStatus(state, maxSequence),
    currentSnapshot,
    currentCommand,
    currentTransition,
    currentTime: currentSnapshot?.time || state.currentTime
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

function clampSequence(sequence, maxSequence) {
  if (!Number.isFinite(sequence)) {
    return 0
  }

  return Math.max(0, Math.min(sequence, maxSequence))
}

function maxSnapshotSequence(bundle) {
  const sequences = (bundle?.snapshots || [])
    .map((snapshot) => snapshot.sequence)
    .filter(Number.isFinite)

  return Math.max(0, ...sequences)
}

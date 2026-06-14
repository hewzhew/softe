# Station Runtime First Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current course sandbox into a first-phase station runtime view with two top-level modes: live operation and simulation.

**Architecture:** Keep the existing Spring Boot + Vue + Element Plus stack and avoid a backend rewrite. Add runtime/session metadata to existing DTOs, reuse `/api/station/snapshot` for live mode, reuse `/api/scenarios/course-sample/run` for simulation mode, and add lightweight frontend mode controls and rendering boundaries.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Vue 3, Element Plus, Node built-in test runner, Vite.

---

## File Structure

Backend files:

- Modify `backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java`
  - Add `TimeSession` and `SourceSummary`.
  - Add source metadata to `ScenarioCommand`.
  - Add session/source metadata to `ReplayBundle`.
- Modify `backend/src/main/java/com/bupt/charging/dto/StationDtos.java`
  - Add live `sessionMode` and `sourceSummary` metadata to `StationSnapshot` with a backwards-compatible constructor.
- Modify `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
  - Populate course simulation metadata in the replay bundle.
- Modify `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
  - Populate live snapshot metadata through the new constructor or explicit fields.
- Modify `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
  - Verify the course replay exposes `SIMULATION`, `COURSE_SEQUENCE`, and `PRECOMPUTED`.
- Modify `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`
  - Verify `/api/station/snapshot` exposes `LIVE` metadata and `/api/scenarios/course-sample/run` exposes simulation metadata.

Frontend files:

- Create `frontend/src/utils/runtimeMode.js`
  - Centralize runtime mode labels and capability rules.
- Create `frontend/src/utils/runtimeMode.test.js`
  - Test mode normalization, capability rules, and source summary formatting.
- Modify `frontend/src/utils/simulationPlayback.js`
  - Preserve replay behavior and expose a small `timelineWindow` helper for timeline rendering.
- Modify `frontend/src/utils/simulationPlayback.test.js`
  - Test the timeline window helper and simulation metadata defaults.
- Create `frontend/src/components/simulation/RuntimeModeSwitch.vue`
  - Two-state segmented control for `实时` and `推演`.
- Create `frontend/src/components/simulation/LiveStationPanel.vue`
  - Live snapshot panel with refresh, metrics, and `StationMap`.
- Create `frontend/src/components/simulation/SimulationBranchPanel.vue`
  - Read-only simulation branch area showing `baseTime`, branch status, and first-phase limitations.
- Modify `frontend/src/components/simulation/ScenarioLoader.vue`
  - Rename visible copy from course-only wording toward event source wording while preserving course sequence loading.
- Modify `frontend/src/components/simulation/EventTimeline.vue`
  - Render only a window around the current event for performance.
- Modify `frontend/src/components/simulation/PlaybackInspector.vue`
  - Show event source metadata and commit state.
- Modify `frontend/src/components/simulation/StationMap.vue`
  - Accept a `mode` prop and display live/simulation source tags.
- Modify `frontend/src/views/SimulationSandbox.vue`
  - Become the station runtime view container with `LIVE` and `SIMULATION` modes.
- Modify `frontend/src/App.vue`
  - Rename top-level tab from `调度沙盘` to `站点运行`.
- Modify `frontend/src/styles.css`
  - Add scoped support classes for runtime mode header, live panel, branch panel, source tags, and timeline window text.

Docs:

- Modify `docs/demo-script.md`
  - Align the demo script with the station runtime view and the `实时 / 推演` split.

## Task 1: Backend Runtime Metadata

**Files:**
- Modify: `backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java`
- Modify: `backend/src/main/java/com/bupt/charging/dto/StationDtos.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
- Test: `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
- Test: `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`

- [ ] **Step 1: Write failing backend assertions**

Add these assertions to `courseSampleReplayExposesCommandsSnapshotsTransitionsAndLegacyRows` in `AcceptanceScenarioServiceTest.java` after the scenario assertions:

```java
assertEquals("SIMULATION", replay.session().mode());
assertEquals("06:00", replay.session().baseTime());
assertEquals("PRECOMPUTED", replay.session().materializationPolicy());
assertEquals("COURSE_SEQUENCE", replay.sourceSummary().primarySourceType());
assertEquals(36, replay.sourceSummary().eventCount());
assertEquals(37, replay.sourceSummary().snapshotCount());
assertEquals("COURSE_SEQUENCE", replay.commands().get(0).sourceType());
assertEquals("PROVISIONAL", replay.commands().get(0).commitState());
```

Add these assertions to `courseScenarioReplayEndpointReturnsPlaybackBundle` in `RestApiSmokeTest.java` after reading `data`:

```java
assertEquals("SIMULATION", data.path("session").path("mode").asText());
assertEquals("COURSE_SEQUENCE", data.path("sourceSummary").path("primarySourceType").asText());
assertEquals("PRECOMPUTED", data.path("session").path("materializationPolicy").asText());
assertEquals("COURSE_SEQUENCE", data.path("commands").get(0).path("sourceType").asText());
assertEquals("PROVISIONAL", data.path("commands").get(0).path("commitState").asText());
```

Add these assertions to `stationSnapshotEndpointProjectsCurrentBusinessData` in `RestApiSmokeTest.java` after reading `data`:

```java
assertEquals("LIVE", data.path("sessionMode").asText());
assertEquals("LIVE_MANUAL", data.path("sourceSummary").path("primarySourceType").asText());
```

- [ ] **Step 2: Run backend tests to verify failure**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=AcceptanceScenarioServiceTest,RestApiSmokeTest" test
```

Expected: compilation fails because `ReplayBundle.session()`, `ReplayBundle.sourceSummary()`, `ScenarioCommand.sourceType()`, `ScenarioCommand.commitState()`, and `StationSnapshot.sessionMode` do not exist yet.

- [ ] **Step 3: Add DTO metadata**

Update `ScenarioDtos.java` so the records include these definitions:

```java
public record TimeSession(
        String id,
        String mode,
        String baseTime,
        String cursorTime,
        String windowStart,
        String windowEnd,
        String materializationPolicy,
        String branchId
) {
}

public record SourceSummary(
        String primarySourceType,
        String primarySourceName,
        List<String> sourceTypes,
        int eventCount,
        int snapshotCount
) {
}
```

Replace `ScenarioCommand` with:

```java
public record ScenarioCommand(
        long sequence,
        String time,
        String type,
        String targetId,
        String mode,
        String amount,
        String sourceText,
        String displayText,
        String sourceType,
        String commitState,
        String branchId
) {
    public ScenarioCommand(
            long sequence,
            String time,
            String type,
            String targetId,
            String mode,
            String amount,
            String sourceText,
            String displayText
    ) {
        this(
                sequence,
                time,
                type,
                targetId,
                mode,
                amount,
                sourceText,
                displayText,
                "COURSE_SEQUENCE",
                "PROVISIONAL",
                null
        );
    }
}
```

Replace `ReplayBundle` with:

```java
public record ReplayBundle(
        ScenarioDefinition scenario,
        TimeSession session,
        SourceSummary sourceSummary,
        List<ScenarioCommand> commands,
        List<StationSnapshot> snapshots,
        List<ScenarioTransition> transitions,
        List<ScenarioCheck> checks,
        List<AcceptanceDtos.AcceptanceEventRow> tableRows
) {
}
```

Update `StationDtos.java` by adding:

```java
public record SourceSummary(
        String primarySourceType,
        String primarySourceName,
        List<String> sourceTypes,
        int eventCount,
        int snapshotCount
) {
}
```

Replace `StationSnapshot` with:

```java
public record StationSnapshot(
        String time,
        StationState station,
        Map<String, VehicleState> vehicles,
        Metrics metrics,
        String sessionMode,
        SourceSummary sourceSummary
) {
    public StationSnapshot(
            String time,
            StationState station,
            Map<String, VehicleState> vehicles,
            Metrics metrics
    ) {
        this(
                time,
                station,
                vehicles,
                metrics,
                "LIVE",
                new SourceSummary("LIVE_MANUAL", "当前站点", List.of("LIVE_MANUAL"), 0, 1)
        );
    }
}
```

- [ ] **Step 4: Populate replay metadata**

In `AcceptanceScenarioService.runCourseSampleReplay()`, replace the `new ScenarioDtos.ReplayBundle(...)` call with this shape:

```java
return new ScenarioDtos.ReplayBundle(
        scenario,
        new ScenarioDtos.TimeSession(
                "course-sample-session",
                "SIMULATION",
                scenario.startTime(),
                scenario.startTime(),
                scenario.startTime(),
                scenario.stopTime(),
                "PRECOMPUTED",
                null
        ),
        new ScenarioDtos.SourceSummary(
                "COURSE_SEQUENCE",
                "课程事件序列",
                List.of("COURSE_SEQUENCE", "SYSTEM_DERIVED"),
                commands.size(),
                snapshots.size()
        ),
        commands,
        snapshots,
        transitions,
        checks,
        tableRows
);
```

If the local variable names differ, keep the same field values and use the existing lists from the method.

- [ ] **Step 5: Populate live snapshot metadata**

In `StationSnapshotService.currentSnapshot()`, keep the existing constructor if Task 1 Step 3 added the backwards-compatible constructor. If explicit metadata is preferred, return:

```java
return new StationDtos.StationSnapshot(
        LocalDateTime.now().format(TIME_FORMAT),
        new StationDtos.StationState(waitingArea, fastPiles, slowPiles),
        vehicles,
        new StationDtos.Metrics(waitingArea.size(), pileQueueCount, faultPileCount, activePileCount),
        "LIVE",
        new StationDtos.SourceSummary(
                "LIVE_MANUAL",
                "当前站点",
                List.of("LIVE_MANUAL", "SYSTEM_DERIVED"),
                waitingArea.size() + pileQueueCount,
                1
        )
);
```

- [ ] **Step 6: Run backend tests to verify pass**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=AcceptanceScenarioServiceTest,RestApiSmokeTest" test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit backend metadata**

Run:

```powershell
git add backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java backend/src/main/java/com/bupt/charging/dto/StationDtos.java backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java
git commit -m "feat: add station runtime metadata"
```

## Task 2: Frontend Runtime Mode Utility

**Files:**
- Create: `frontend/src/utils/runtimeMode.js`
- Create: `frontend/src/utils/runtimeMode.test.js`

- [ ] **Step 1: Write failing runtime mode tests**

Create `frontend/src/utils/runtimeMode.test.js`:

```js
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
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/runtimeMode.test.js
```

Expected: test runner fails because `runtimeMode.js` does not exist.

- [ ] **Step 3: Implement runtime mode utility**

Create `frontend/src/utils/runtimeMode.js`:

```js
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
```

- [ ] **Step 4: Run frontend utility tests to verify pass**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/runtimeMode.test.js
```

Expected: runtime mode tests pass.

- [ ] **Step 5: Commit runtime utility**

Run:

```powershell
git add frontend/src/utils/runtimeMode.js frontend/src/utils/runtimeMode.test.js
git commit -m "feat: add runtime mode helpers"
```

## Task 3: Timeline Window Helper

**Files:**
- Modify: `frontend/src/utils/simulationPlayback.js`
- Modify: `frontend/src/utils/simulationPlayback.test.js`
- Modify: `frontend/src/components/simulation/EventTimeline.vue`

- [ ] **Step 1: Write failing helper test**

Add `visibleTimelineCommands` to the import list in `simulationPlayback.test.js`, then append:

```js
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
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/simulationPlayback.test.js
```

Expected: import fails because `visibleTimelineCommands` does not exist.

- [ ] **Step 3: Implement timeline window helper**

Add this export near the other exported helpers in `simulationPlayback.js`:

```js
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
```

- [ ] **Step 4: Use the helper in EventTimeline**

Modify `EventTimeline.vue`:

```vue
<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">时间轴</p>
        <h2>事件进度</h2>
      </div>
      <el-tag effect="plain">{{ currentSequence }} / {{ commands.length }}</el-tag>
    </div>

    <p v-if="commands.length > visibleCommands.length" class="timeline-window-note">
      当前显示 {{ visibleCommands.length }} / {{ commands.length }} 个事件，播放时自动跟随当前位置
    </p>

    <div class="event-timeline">
      <button
        v-for="command in visibleCommands"
        :key="command.sequence"
        type="button"
        class="timeline-point"
        :class="{ active: command.sequence === currentSequence, past: command.sequence < currentSequence }"
        @click="$emit('seek', command.sequence)"
      >
        <span>{{ command.time }}</span>
        <strong>{{ command.targetId }}</strong>
      </button>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { visibleTimelineCommands } from '../../utils/simulationPlayback'

const props = defineProps({
  commands: { type: Array, default: () => [] },
  currentSequence: { type: Number, default: 0 },
  windowSize: { type: Number, default: 18 }
})

defineEmits(['seek'])

const visibleCommands = computed(() => visibleTimelineCommands(
  props.commands,
  props.currentSequence,
  props.windowSize
))
</script>
```

Add this CSS to `frontend/src/styles.css`:

```css
.timeline-window-note {
  margin: -4px 0 10px;
  color: #64748b;
  font-size: 12px;
}
```

- [ ] **Step 5: Run frontend tests**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/simulationPlayback.test.js
```

Expected: simulation playback tests pass.

- [ ] **Step 6: Commit timeline windowing**

Run:

```powershell
git add frontend/src/utils/simulationPlayback.js frontend/src/utils/simulationPlayback.test.js frontend/src/components/simulation/EventTimeline.vue frontend/src/styles.css
git commit -m "feat: window simulation timeline events"
```

## Task 4: Runtime View Components

**Files:**
- Create: `frontend/src/components/simulation/RuntimeModeSwitch.vue`
- Create: `frontend/src/components/simulation/LiveStationPanel.vue`
- Create: `frontend/src/components/simulation/SimulationBranchPanel.vue`
- Modify: `frontend/src/components/simulation/StationMap.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create runtime mode switch**

Create `RuntimeModeSwitch.vue`:

```vue
<template>
  <el-card class="runtime-mode-card" shadow="never">
    <div class="runtime-mode-copy">
      <p class="eyebrow">站点运行台</p>
      <h2>实时与推演</h2>
      <span>{{ description }}</span>
    </div>

    <el-segmented
      :model-value="modelValue"
      :options="modeOptions"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { RUNTIME_MODES } from '../../utils/runtimeMode'

const props = defineProps({
  modelValue: { type: String, default: RUNTIME_MODES.LIVE }
})

defineEmits(['update:modelValue'])

const modeOptions = [
  { label: '实时', value: RUNTIME_MODES.LIVE },
  { label: '推演', value: RUNTIME_MODES.SIMULATION }
]

const description = computed(() => (
  props.modelValue === RUNTIME_MODES.SIMULATION
    ? '使用课程事件序列或临时分支观察非当前时刻的站点状态'
    : '跟随当前业务数据，一秒一秒观察站点运行'
))
</script>
```

- [ ] **Step 2: Create live station panel**

Create `LiveStationPanel.vue`:

```vue
<template>
  <div class="live-station-panel">
    <el-card shadow="never">
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">实时站点</p>
          <h2>当前业务状态</h2>
        </div>
        <el-button type="primary" :loading="loading" @click="$emit('refresh')">刷新</el-button>
      </div>

      <div class="runtime-metrics">
        <div>
          <span>等候车辆</span>
          <strong>{{ snapshot?.metrics?.waitingCount ?? 0 }}</strong>
        </div>
        <div>
          <span>桩内队列</span>
          <strong>{{ snapshot?.metrics?.pileQueueCount ?? 0 }}</strong>
        </div>
        <div>
          <span>故障桩</span>
          <strong>{{ snapshot?.metrics?.faultPileCount ?? 0 }}</strong>
        </div>
        <div>
          <span>可服务桩</span>
          <strong>{{ snapshot?.metrics?.activePileCount ?? 0 }}</strong>
        </div>
      </div>
    </el-card>

    <StationMap :snapshot="snapshot" mode="LIVE" />
  </div>
</template>

<script setup>
import StationMap from './StationMap.vue'

defineProps({
  snapshot: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

defineEmits(['refresh'])
</script>
```

- [ ] **Step 3: Create simulation branch panel**

Create `SimulationBranchPanel.vue`:

```vue
<template>
  <el-card shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">推演分支</p>
        <h2>{{ branchName }}</h2>
      </div>
      <el-tag effect="plain">只读</el-tag>
    </div>

    <el-descriptions :column="1" border>
      <el-descriptions-item label="会话模式">{{ session?.mode || 'SIMULATION' }}</el-descriptions-item>
      <el-descriptions-item label="基准时间">{{ session?.baseTime || scenario?.startTime || '--:--' }}</el-descriptions-item>
      <el-descriptions-item label="投影策略">{{ session?.materializationPolicy || 'PRECOMPUTED' }}</el-descriptions-item>
      <el-descriptions-item label="事件来源">{{ sourceName }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { formatSourceSummary } from '../../utils/runtimeMode'

const props = defineProps({
  session: { type: Object, default: null },
  sourceSummary: { type: Object, default: null },
  scenario: { type: Object, default: null }
})

const branchName = computed(() => props.session?.branchId || '课程推演分支')
const sourceName = computed(() => formatSourceSummary(props.sourceSummary))
</script>
```

- [ ] **Step 4: Add mode tags to StationMap**

In `StationMap.vue`, add a `mode` prop and source tag:

```vue
<el-tag :type="mode === 'LIVE' ? 'success' : 'warning'" effect="plain">
  {{ mode === 'LIVE' ? '实时' : '推演' }} · {{ snapshot?.time || '--:--' }}
</el-tag>
```

Use this prop block:

```js
const props = defineProps({
  snapshot: { type: Object, default: null },
  mode: { type: String, default: 'SIMULATION' }
})
```

Replace the old single time tag in the card heading with the new tag above.

- [ ] **Step 5: Add component CSS**

Add this CSS to `frontend/src/styles.css`:

```css
.runtime-mode-card :deep(.el-card__body),
.runtime-mode-card {
  min-width: 0;
}

.runtime-mode-card :deep(.el-card__body) {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.runtime-mode-copy h2 {
  margin: 2px 0 4px;
  color: #172033;
  font-size: 20px;
  font-weight: 650;
}

.runtime-mode-copy span {
  color: #64748b;
  font-size: 13px;
}

.live-station-panel {
  display: grid;
  gap: 16px;
}

.runtime-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.runtime-metrics div {
  min-height: 68px;
  padding: 12px;
  border: 1px solid #e4eaf1;
  border-radius: 6px;
  background: #f8fafc;
}

.runtime-metrics span,
.runtime-metrics strong {
  display: block;
}

.runtime-metrics span {
  color: #64748b;
  font-size: 12px;
}

.runtime-metrics strong {
  margin-top: 6px;
  color: #172033;
  font-size: 20px;
}
```

Add responsive CSS inside the existing `@media (max-width: 720px)` block:

```css
.runtime-mode-card :deep(.el-card__body) {
  align-items: flex-start;
  flex-direction: column;
}

.runtime-metrics {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
```

- [ ] **Step 6: Run frontend build**

Run:

```powershell
cd D:\softe\frontend
npm run build
```

Expected: Vite build succeeds. Existing non-fatal chunk warnings are acceptable if they already existed.

- [ ] **Step 7: Commit runtime components**

Run:

```powershell
git add frontend/src/components/simulation/RuntimeModeSwitch.vue frontend/src/components/simulation/LiveStationPanel.vue frontend/src/components/simulation/SimulationBranchPanel.vue frontend/src/components/simulation/StationMap.vue frontend/src/styles.css
git commit -m "feat: add station runtime components"
```

## Task 5: Wire Runtime View

**Files:**
- Modify: `frontend/src/views/SimulationSandbox.vue`
- Modify: `frontend/src/components/simulation/ScenarioLoader.vue`
- Modify: `frontend/src/components/simulation/PlaybackInspector.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Update ScenarioLoader copy and props**

Change `ScenarioLoader.vue` visible text so it reads as an event source loader:

```vue
<p class="eyebrow">事件来源</p>
<h2>{{ scenario?.name || '课程事件序列' }}</h2>
```

Change metric labels:

```vue
<span>事件数量</span>
<span>快照数量</span>
```

Keep the primary button text as:

```vue
加载课程事件序列
```

- [ ] **Step 2: Update PlaybackInspector metadata**

Add a source tag under the title in `PlaybackInspector.vue`:

```vue
<el-tag v-if="command" effect="plain">{{ command.sourceType || 'COURSE_SEQUENCE' }}</el-tag>
```

Add two descriptions after `来源`:

```vue
<el-descriptions-item label="事件来源">{{ command.sourceType || 'COURSE_SEQUENCE' }}</el-descriptions-item>
<el-descriptions-item label="提交状态">{{ command.commitState || 'PROVISIONAL' }}</el-descriptions-item>
```

- [ ] **Step 3: Wire SimulationSandbox as runtime container**

Modify `SimulationSandbox.vue` imports:

```js
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import LiveStationPanel from '../components/simulation/LiveStationPanel.vue'
import RuntimeModeSwitch from '../components/simulation/RuntimeModeSwitch.vue'
import SimulationBranchPanel from '../components/simulation/SimulationBranchPanel.vue'
import { RUNTIME_MODES } from '../utils/runtimeMode'
```

Add state:

```js
const runtimeMode = ref(RUNTIME_MODES.LIVE)
const liveLoading = ref(false)
const liveSnapshot = ref(null)
```

Add computed metadata:

```js
const session = computed(() => bundle.value?.session || null)
const sourceSummary = computed(() => bundle.value?.sourceSummary || null)
```

Add live refresh:

```js
async function refreshLiveSnapshot() {
  liveLoading.value = true
  try {
    liveSnapshot.value = await api.getStationSnapshot()
  } catch (error) {
    ElMessage.error(error.message || '实时状态加载失败')
  } finally {
    liveLoading.value = false
  }
}

onMounted(() => {
  refreshLiveSnapshot()
})
```

Update template so the first child is:

```vue
<RuntimeModeSwitch v-model="runtimeMode" />
```

Then render live mode:

```vue
<LiveStationPanel
  v-if="runtimeMode === RUNTIME_MODES.LIVE"
  :snapshot="liveSnapshot"
  :loading="liveLoading"
  @refresh="refreshLiveSnapshot"
/>
```

Wrap the existing simulation loader, clock, layout, timeline, inspector, and verification markup in:

```vue
<template v-else>
  <!-- existing simulation content -->
</template>
```

Inside the simulation side, render branch panel before inspector:

```vue
<SimulationBranchPanel
  :session="session"
  :source-summary="sourceSummary"
  :scenario="scenario"
/>
```

Pass the mode to `StationMap`:

```vue
<StationMap :snapshot="playback.currentSnapshot" mode="SIMULATION" />
```

- [ ] **Step 4: Rename top-level tab**

In `App.vue`, change:

```vue
<el-tab-pane label="调度沙盘" name="simulation">
```

to:

```vue
<el-tab-pane label="站点运行" name="simulation">
```

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: all frontend tests pass and Vite build succeeds.

- [ ] **Step 6: Commit runtime wiring**

Run:

```powershell
git add frontend/src/views/SimulationSandbox.vue frontend/src/components/simulation/ScenarioLoader.vue frontend/src/components/simulation/PlaybackInspector.vue frontend/src/App.vue
git commit -m "feat: wire station runtime modes"
```

## Task 6: Demo Script and Final Verification

**Files:**
- Modify: `docs/demo-script.md`

- [ ] **Step 1: Update demo script wording**

Replace the first demo heading:

```markdown
## 演示一：站点运行台
```

Use this flow:

```markdown
1. 进入“站点运行”。
2. 保持“实时”模式，点击刷新，说明这里显示当前业务数据。
3. 切换到“推演”模式。
4. 加载课程事件序列。
5. 点击播放，观察车辆依次进入等候区、快充队列和慢充队列。
6. 使用“下一步”推进关键事件，说明每次请求提交、队列分配、故障和恢复后的状态变化。
7. 查看右侧“推演分支”，说明课程回放和未来预测以后都会归入推演会话。
8. 播放结束后展开结果核对，确认关键时刻的车辆位置和队列状态。
9. 点击“复制结果”，将结果表复制到汇报材料或临时表格中。
```

- [ ] **Step 2: Run full backend verification**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 3: Run full frontend verification**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: all frontend tests pass and Vite build succeeds.

- [ ] **Step 4: Browser verification**

Start servers if needed:

```powershell
cd D:\softe\backend
mvn spring-boot:run
```

```powershell
cd D:\softe\frontend
npm run dev
```

Open:

```text
http://127.0.0.1:5173/
```

Manual checks:

1. The top-level tab reads `站点运行`.
2. `实时` mode shows metrics and a station map loaded from `/api/station/snapshot`.
3. `实时` mode does not show speed controls.
4. `推演` mode shows `加载课程事件序列`.
5. Loading the course sequence shows branch/session metadata.
6. Playback, pause, step forward, step back, reset, speed, and timeline seek still work.
7. Timeline shows a bounded event window note when command count exceeds the window size.
8. Results can still be expanded and copied.
9. Switching back to `实时` does not carry over simulation vehicles into the live snapshot.

- [ ] **Step 5: Commit docs and final fixes**

Run:

```powershell
git add docs/demo-script.md
git commit -m "docs: update station runtime demo script"
```

If final fixes were needed during verification, stage the changed files from this plan explicitly. The expected files for final fixes are:

```text
frontend/src/views/SimulationSandbox.vue
frontend/src/components/simulation/EventTimeline.vue
frontend/src/components/simulation/LiveStationPanel.vue
frontend/src/components/simulation/RuntimeModeSwitch.vue
frontend/src/components/simulation/SimulationBranchPanel.vue
frontend/src/components/simulation/ScenarioLoader.vue
frontend/src/components/simulation/StationMap.vue
frontend/src/components/simulation/PlaybackInspector.vue
frontend/src/styles.css
docs/demo-script.md
```

Use this command when the final fixes touch those files:

```powershell
git add frontend/src/views/SimulationSandbox.vue frontend/src/components/simulation/EventTimeline.vue frontend/src/components/simulation/LiveStationPanel.vue frontend/src/components/simulation/RuntimeModeSwitch.vue frontend/src/components/simulation/SimulationBranchPanel.vue frontend/src/components/simulation/ScenarioLoader.vue frontend/src/components/simulation/StationMap.vue frontend/src/components/simulation/PlaybackInspector.vue frontend/src/styles.css docs/demo-script.md
git commit -m "fix: polish station runtime verification"
```

## Plan Self-Review

Spec coverage:

- `实时 / 推演` top-level split is covered by Tasks 2, 4, and 5.
- Course sequence remains available in simulation mode through Tasks 1 and 5.
- Live snapshot reuse is covered by Tasks 1 and 5.
- Session/source metadata is covered by Tasks 1 and 5.
- Branch placeholder is covered by Task 4.
- Performance boundary is covered by Task 3 timeline windowing and Task 6 browser checks.
- Heavy libraries are not introduced.

Known deferrals:

- Real forecast branch persistence is not implemented in this phase.
- Event-source import APIs are not implemented in this phase.
- Live preset operations are not implemented in this phase.
- Historical real event replay is not implemented in this phase.

Verification commands:

```powershell
cd D:\softe\backend
mvn test
```

```powershell
cd D:\softe\frontend
npm test
npm run build
```

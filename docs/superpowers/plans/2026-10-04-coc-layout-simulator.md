# Clash of Clans Layout Generation & Simulation Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade, standalone local web application and simulation framework for Clash of Clans layout generation, optimization (NSGA-II heuristic scoring), headless and visual real-time combat simulation with troop AI and heroes, procedural isometric rendering, and CoC Codex statistics.

**Architecture:** Pure vanilla modern ES modules without runtime server dependencies. An isometric $44 \times 44$ spatial grid engine handles building bounds, wall connectivity, and drop-zone buffers. An optimization engine evaluates core protection, spell clustering, coverage heatmaps, and wall compartments. A discrete-event combat simulator executes $A^*$ pathfinding with dynamic wall resistance, the "Rule of 3" troop targeting logic, hero abilities, and projectile physics. A polished Clash-themed HTML5/Canvas UI provides real-time battle testing, live layout editing, and JSON export/import.

**Tech Stack:** JavaScript (ES2022 / ES Modules), HTML5 Canvas 2D, CSS3 (Clash Theme), Node.js native test runner (`node --test`) for unit testing.

**Spec:** `docs/superpowers/specs/2026-10-04-coc-layout-simulator-design.md`

## Global Constraints

- Standalone: runs directly in any modern browser without external servers or internet connection.
- Grid: precisely $44 \times 44$ tiles with coordinate boundaries $X \in [0, 43], Y \in [0, 43]$.
- Spawner buffer: exact 1-tile exclusion buffer around all placed buildings.
- Town Hall coverage: full balance data and templates for TH9 through TH16.
- Code style: clean ES modules compatible with both browser `<script type="module">` and Node.js test execution.

## Review Focus

1. Out-of-bounds building placement: placing a $4\times 4$ building at $(42, 42)$ must be rejected by boundary checks without throwing errors.
2. Zero-damage or dead-lock pathfinding: if an airborne or ranged troop is surrounded by walls, $A^*$ must not hang or infinite-loop.
3. Rapid wall deletion/modification: modifying walls during active battle simulation must gracefully re-evaluate paths without crashing.
4. Rule of 3 edge case: when fewer than 3 valid targets remain on the map (e.g. 1 or 2 buildings left), targeting must pick the closest available without index errors.
5. JSON layout import validation: importing malformed or corrupted JSON must show user-friendly feedback instead of crashing the canvas renderer.

---

### Task 1: Game Data Layer & Pre-configured Layouts

**Files:**
- Create: `js/data/buildings.js`
- Create: `js/data/troops.js`
- Create: `js/data/default_layouts.js`
- Test: `tests/data.test.js`

**Interfaces:**
- Produces:
  - `BUILDING_DEFS`: Object dictionary mapping building types to footprints ($1\times 1$ to $5\times 5$), category (`defense`, `resource`, `army`, `other`), range, target preference (`ground`, `air`, `both`), DPS, HP, and visual colors.
  - `TROOP_DEFS`: Object dictionary mapping troop types to HP, DPS, movement speed, attack range, attack speed, housing space, target preference (`defenses`, `walls`, `any`), and hero ability stats.
  - `DEFAULT_LAYOUTS`: Dictionary of validated layouts for TH9, TH11, TH13, TH15, TH16 with building placements and wall coordinates.

- [ ] **Step 1: Write failing unit test for data integrity**

```javascript
// tests/data.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { BUILDING_DEFS } from '../js/data/buildings.js';
import { TROOP_DEFS } from '../js/data/troops.js';
import { DEFAULT_LAYOUTS } from '../js/data/default_layouts.js';

test('BUILDING_DEFS has valid Town Hall and defense definitions', () => {
  assert.ok(BUILDING_DEFS.town_hall, 'Town Hall definition exists');
  assert.equal(BUILDING_DEFS.town_hall.size, 4);
  assert.ok(BUILDING_DEFS.eagle_artillery, 'Eagle Artillery exists');
  assert.equal(BUILDING_DEFS.eagle_artillery.size, 4);
  assert.equal(BUILDING_DEFS.cannon.size, 3);
  assert.equal(BUILDING_DEFS.wall.size, 1);
});

test('TROOP_DEFS includes giants with defense preference and wall breakers with walls preference', () => {
  assert.ok(TROOP_DEFS.giant);
  assert.equal(TROOP_DEFS.giant.targetPreference, 'defenses');
  assert.ok(TROOP_DEFS.wall_breaker);
  assert.equal(TROOP_DEFS.wall_breaker.targetPreference, 'walls');
  assert.ok(TROOP_DEFS.barbarian_king);
  assert.ok(TROOP_DEFS.archer_queen);
});

test('DEFAULT_LAYOUTS contains valid TH11 and TH15 bases', () => {
  assert.ok(DEFAULT_LAYOUTS['TH11']);
  assert.ok(DEFAULT_LAYOUTS['TH15']);
  assert.ok(Array.isArray(DEFAULT_LAYOUTS['TH11'].buildings));
  assert.ok(Array.isArray(DEFAULT_LAYOUTS['TH11'].walls));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/data.test.js`
Expected: FAIL (Cannot find module)

- [ ] **Step 3: Implement `buildings.js`, `troops.js`, and `default_layouts.js`**

Implement comprehensive dictionaries in `js/data/buildings.js`, `js/data/troops.js`, and `js/data/default_layouts.js`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/data.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/data/ tests/data.test.js
git commit -m "feat(data): add building definitions, troop stats, and default layouts"
```

---

### Task 2: Village Spatial Grid & Collision Engine

**Files:**
- Create: `js/engine/grid.js`
- Test: `tests/grid.test.js`

**Interfaces:**
- Consumes: `BUILDING_DEFS` from `js/data/buildings.js`
- Produces:
  - `VillageGrid` class:
    - `constructor(size = 44)`
    - `canPlaceBuilding(type, x, y): boolean`
    - `placeBuilding(type, x, y, level): object | null`
    - `removeBuildingAt(x, y): boolean`
    - `placeWall(x, y, level): boolean`
    - `removeWall(x, y): boolean`
    - `getSpawnerBuffer(): Uint8Array / 2D array`
    - `isDropZoneValid(x, y): boolean`
    - `getWallAdjacency(x, y): { north: boolean, south: boolean, east: boolean, west: boolean }`
    - `exportLayout(): object`
    - `importLayout(jsonObj): boolean`

- [ ] **Step 1: Write failing test for grid logic**

```javascript
// tests/grid.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';

test('VillageGrid enforces 44x44 bounds and rejects overlapping buildings', () => {
  const grid = new VillageGrid(44);
  assert.equal(grid.canPlaceBuilding('town_hall', 20, 20), true);
  const th = grid.placeBuilding('town_hall', 20, 20, 11);
  assert.ok(th);

  // Overlap should be rejected
  assert.equal(grid.canPlaceBuilding('cannon', 21, 21), false);

  // Out of bounds should be rejected
  assert.equal(grid.canPlaceBuilding('town_hall', 42, 42), false);
  assert.equal(grid.canPlaceBuilding('cannon', -1, 10), false);
});

test('VillageGrid calculates 1-tile spawner buffer correctly', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('cannon', 10, 10, 10); // cannon is 3x3: tiles 10,11,12 x 10,11,12
  // Buffer extends 1 tile around: [9..13] x [9..13]
  assert.equal(grid.isDropZoneValid(9, 9), false, 'Buffer tile is invalid for dropping');
  assert.equal(grid.isDropZoneValid(11, 11), false, 'Occupied tile is invalid for dropping');
  assert.equal(grid.isDropZoneValid(8, 8), true, 'Outside buffer is valid for dropping');
});

test('VillageGrid manages wall adjacency', () => {
  const grid = new VillageGrid(44);
  grid.placeWall(15, 15);
  grid.placeWall(15, 16);
  const adj = grid.getWallAdjacency(15, 15);
  assert.equal(adj.south, true);
  assert.equal(adj.north, false);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/grid.test.js`
Expected: FAIL

- [ ] **Step 3: Implement `js/engine/grid.js`**

Implement `VillageGrid` class adhering to boundary checks, 1-tile drop-zone dilation, wall matrices, and JSON import/export.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/grid.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/engine/grid.js tests/grid.test.js
git commit -m "feat(engine): implement VillageGrid with collision, spawner buffer, and wall adjacency"
```

---

### Task 3: Layout Analytics & Genetic Heuristic Optimizer

**Files:**
- Create: `js/engine/optimizer.js`
- Test: `tests/optimizer.test.js`

**Interfaces:**
- Consumes: `VillageGrid` from `js/engine/grid.js`, `BUILDING_DEFS` from `js/data/buildings.js`
- Produces:
  - `LayoutAnalyzer` class:
    - `calculateCoreProtection(grid): number` (0 to 100)
    - `calculateSpellDispersion(grid): number` (0 to 100)
    - `calculateCoverageBalance(grid): { groundScore: number, airScore: number, balanceScore: number }`
    - `calculateCompartmentalization(grid): { compartmentsCount: number, qualityScore: number, weakJunctions: Array }`
    - `evaluateBase(grid): { overallScore: number, breakdown: object }`
  - `LayoutGenerator` class:
    - `generateBase(thLevel, style = 'war'): object` (returns optimized layout)
    - `autoOptimize(grid): object`

- [ ] **Step 1: Write failing test for layout optimization & analysis**

```javascript
// tests/optimizer.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { LayoutAnalyzer, LayoutGenerator } from '../js/engine/optimizer.js';
import { DEFAULT_LAYOUTS } from '../js/data/default_layouts.js';

test('LayoutAnalyzer evaluates default layout scores', () => {
  const grid = new VillageGrid(44);
  grid.importLayout(DEFAULT_LAYOUTS['TH11']);

  const report = LayoutAnalyzer.evaluateBase(grid);
  assert.ok(report.overallScore > 0 && report.overallScore <= 100);
  assert.ok(report.breakdown.coreProtection >= 0);
  assert.ok(report.breakdown.spellDispersion >= 0);
  assert.ok(report.breakdown.coverageBalance >= 0);
  assert.ok(report.breakdown.compartmentalization >= 0);
});

test('LayoutGenerator produces balanced, collision-free base for TH11', () => {
  const layout = LayoutGenerator.generateBase(11, 'war');
  assert.ok(layout.buildings.length > 10);
  assert.ok(layout.walls.length > 50);

  const grid = new VillageGrid(44);
  const success = grid.importLayout(layout);
  assert.equal(success, true, 'Generated layout imports with zero collisions');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/optimizer.test.js`
Expected: FAIL

- [ ] **Step 3: Implement `js/engine/optimizer.js`**

Implement analytical scoring functions ($f_1$ to $f_4$) and heuristic generation with radial symmetry and wall compartment synthesis.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/optimizer.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/engine/optimizer.js tests/optimizer.test.js
git commit -m "feat(engine): implement LayoutAnalyzer scoring and LayoutGenerator"
```

---

### Task 4: Dynamic $A^*$ Pathfinding with Wall Resistance

**Files:**
- Create: `js/simulation/pathfinding.js`
- Test: `tests/pathfinding.test.js`

**Interfaces:**
- Consumes: `VillageGrid` from `js/engine/grid.js`
- Produces:
  - `AStarFinder` class:
    - `findPath(startPos, targetPos, grid, options): { path: Array, targetWall: object | null, totalCost: number }`
    - Options: `{ canFly: boolean, troopDps: number, isWallBreaker: boolean }`

- [ ] **Step 1: Write failing test for pathfinding with walls**

```javascript
// tests/pathfinding.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { AStarFinder } from '../js/simulation/pathfinding.js';

test('AStarFinder finds direct path on open terrain', () => {
  const grid = new VillageGrid(44);
  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 10, y: 5 }, grid, { canFly: false, troopDps: 100 });
  assert.ok(result.path.length > 0);
  assert.equal(result.targetWall, null);
});

test('Air troops ignore walls completely', () => {
  const grid = new VillageGrid(44);
  // Put a line of walls between (5, 5) and (10, 5)
  for (let y = 0; y < 10; y++) grid.placeWall(7, y);

  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 10, y: 5 }, grid, { canFly: true, troopDps: 100 });
  assert.ok(result.path.length > 0);
  assert.equal(result.targetWall, null);
});

test('Ground troops choose path around short wall vs breaking high-hp wall', () => {
  const grid = new VillageGrid(44);
  // Small wall with an open gap at y = 7
  grid.placeWall(7, 4);
  grid.placeWall(7, 5);
  grid.placeWall(7, 6);

  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 9, y: 5 }, grid, { canFly: false, troopDps: 10 });
  assert.ok(result.path.length > 0);
  // Expect it to navigate around the wall rather than attacking it
  const wallCoords = result.path.filter(p => p.x === 7 && (p.y >= 4 && p.y <= 6));
  assert.equal(wallCoords.length, 0, 'Troop walked around the 3-tile wall instead of breaking it');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/pathfinding.test.js`
Expected: FAIL

- [ ] **Step 3: Implement `js/simulation/pathfinding.js`**

Implement $A^*$ algorithm using min-heap / priority queue with dynamic wall cost formula: $\text{stepCost} = 1 + \frac{\text{wallHP}}{\text{troopDPS} \times 15}$.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/pathfinding.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/simulation/pathfinding.js tests/pathfinding.test.js
git commit -m "feat(simulation): implement A* pathfinding with dynamic wall penalties"
```

---

### Task 5: Troop AI Logic, Rule of 3, & Heroes System

**Files:**
- Create: `js/simulation/ai.js`
- Test: `tests/ai.test.js`

**Interfaces:**
- Consumes: `TROOP_DEFS` from `js/data/troops.js`, `AStarFinder` from `js/simulation/pathfinding.js`
- Produces:
  - `TroopInstance` class
  - `HeroInstance` class (extends `TroopInstance` with active abilities)
  - `TroopAI` class:
    - `evaluateRuleOfThree(troop, grid, buildings): TargetCandidate`
    - `updateTroop(troop, deltaSeconds, combatState): void`

- [ ] **Step 1: Write failing test for Troop AI and Rule of 3**

```javascript
// tests/ai.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { TroopInstance, HeroInstance, TroopAI } from '../js/simulation/ai.js';
import { TROOP_DEFS } from '../js/data/troops.js';

test('Troop targeting: Giants prioritize defenses over closer resource buildings', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('gold_storage', 10, 10, 11); // closer
  grid.placeBuilding('cannon', 16, 10, 11);       // further defense

  const giant = new TroopInstance('giant', { x: 5, y: 10 });
  const target = TroopAI.findTarget(giant, grid);

  assert.ok(target);
  assert.equal(target.type, 'cannon', 'Giant targeted the cannon defense ignoring the gold storage');
});

test('Rule of 3: Evaluates 3 closest buildings and picks optimal target', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('barracks', 10, 10, 1);
  grid.placeBuilding('army_camp', 12, 10, 1);
  grid.placeBuilding('builder_hut', 14, 10, 1);
  grid.placeBuilding('elixir_collector', 30, 30, 1); // far away

  const barb = new TroopInstance('barbarian', { x: 5, y: 10 });
  const candidates = TroopAI.getCandidates(barb, grid, 3);
  assert.equal(candidates.length, 3);
  assert.ok(!candidates.some(c => c.type === 'elixir_collector'), 'Far building not in rule of 3');
});

test('Hero abilities: Barbarian King activates Iron Fist', () => {
  const bk = new HeroInstance('barbarian_king', { x: 10, y: 10 });
  const initialHp = bk.currentHp = 1000;
  bk.activateAbility();
  assert.ok(bk.currentHp > initialHp, 'Iron Fist recovered HP');
  assert.equal(bk.isAbilityActive, true);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/ai.test.js`
Expected: FAIL

- [ ] **Step 3: Implement `js/simulation/ai.js`**

Implement `TroopInstance`, `HeroInstance`, targeting filters, Rule of 3, and state machine (MOVING, ATTACKING, IDLE, DEAD).

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/ai.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/simulation/ai.js tests/ai.test.js
git commit -m "feat(simulation): implement Troop AI, Rule of 3, and Hero ability system"
```

---

### Task 6: Headless Tactical Combat Simulation Loop & Projectiles

**Files:**
- Create: `js/simulation/combat.js`
- Create: `js/simulation/fx.js`
- Test: `tests/combat.test.js`

**Interfaces:**
- Consumes: `VillageGrid`, `TroopAI`, `BUILDING_DEFS`
- Produces:
  - `CombatSimulator` class:
    - `constructor(grid, army)`
    - `step(deltaTime = 0.1): CombatState`
    - `runToCompletion(maxDuration = 180): CombatSummary`
    - `deployTroop(type, x, y): TroopInstance | null`
    - `getState(): CombatState`
  - `CombatSummary`: `{ stars: number, destructionPercent: number, duration: number, logs: Array }`

- [ ] **Step 1: Write failing test for discrete combat loop**

```javascript
// tests/combat.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { CombatSimulator } from '../js/simulation/combat.js';

test('CombatSimulator calculates destruction percentage and stars', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('town_hall', 20, 20, 11);
  grid.placeBuilding('cannon', 26, 20, 11);

  const army = [
    { type: 'giant', count: 5, x: 35, y: 20 },
    { type: 'archer', count: 15, x: 35, y: 22 }
  ];

  const sim = new CombatSimulator(grid, army);
  const summary = sim.runToCompletion(120);

  assert.ok(summary.destructionPercent >= 0 && summary.destructionPercent <= 100);
  assert.ok(summary.stars >= 0 && summary.stars <= 3);
  assert.ok(summary.duration > 0);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/combat.test.js`
Expected: FAIL

- [ ] **Step 3: Implement `js/simulation/combat.js` and `js/simulation/fx.js`**

Implement defenses firing logic, projectile arcs (Mortar splash, Archer arrows, Air Defense rockets, Inferno beam ramping damage), Wall damage, HP deductions, stars and percentage computation.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/combat.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add js/simulation/combat.js js/simulation/fx.js tests/combat.test.js
git commit -m "feat(simulation): implement CombatSimulator loop, defense projectiles, and destruction scoring"
```

---

### Task 7: Isometric Canvas Renderer with Clash Aesthetics & VFX

**Files:**
- Create: `js/engine/renderer.js`

**Interfaces:**
- Consumes: `VillageGrid`, `CombatSimulator`, `BUILDING_DEFS`, `TROOP_DEFS`
- Produces:
  - `IsometricRenderer` class:
    - `constructor(canvas)`
    - `render(grid, combatSimulator, viewOptions): void`
    - `screenToGrid(screenX, screenY): { gridX: number, gridY: number }`
    - `gridToScreen(gridX, gridY): { screenX: number, screenY: number }`
    - `setZoom(zoomFactor): void`
    - `pan(dx, dy): void`
    - `drawHeatmap(coverageMatrix): void`
    - `drawSelectionRange(building): void`

- [ ] **Step 1: Implement `IsometricRenderer`**
  - Implement 2.5D coordinate projection with smooth scaling and panning.
  - Implement procedural high-detail vector sprites for buildings (Town Hall with glowing roof, Cannons with metallic barrels, Archer Towers with elevated wooden platforms, Inferno Towers with lava cores, Eagle Artillery with majestic wings).
  - Implement connected Wall rendering (automatic T-junction, straight, and corner connections matching level 11 to 16 obsidian/electric walls).
  - Implement visual health bars above damaged structures and active troops.
  - Implement real-time projectile trajectories, impact flashes, and damage floats.
- [ ] **Step 2: Commit**

```bash
git add js/engine/renderer.js
git commit -m "feat(renderer): implement IsometricRenderer with procedural vector assets and VFX"
```

---

### Task 8: Complete Standalone Web Application UI & CoC Codex

**Files:**
- Create: `index.html`
- Create: `css/style.css`
- Create: `js/app.js`

**Interfaces:**
- Integrates all previous modules into an interactive single-page app.
- Provides:
  - Top bar: Town Hall selector (TH9 to TH16), Base Preset loader, Auto-Optimize button, Analysis breakdown popup, and Battle Mode toggle.
  - Interactive Canvas viewport with drag-to-place, wall painting mode, and range circles.
  - Bottom Deployment dock: Troop cards with counts, Hero activation buttons, and Auto-Attack benchmark button.
  - CoC Codex Modal: Interactive encyclopedia displaying detailed stats (HP, DPS, Range, Targets, Speeds, Level upgrades) for all defenses, troops, and heroes.
  - Layout Export/Import: One-click JSON copy and paste.

- [ ] **Step 1: Implement `index.html`, `css/style.css`, and `js/app.js`**
- [ ] **Step 2: Test complete integration locally**
  - Run all Node.js unit tests: `node --test tests/*.test.js`
  - Verify zero console errors and full UI functionality.
- [ ] **Step 3: Commit**

```bash
git add index.html css/style.css js/app.js
git commit -m "feat(ui): assemble complete standalone web application with Codex and battle controls"
```

---

### Task 9: End-to-End Verification & Benchmark Suite

**Files:**
- Create: `tests/e2e.test.js`

- [ ] **Step 1: Write E2E test verifying base generation, optimization, simulation, and export/import**

```javascript
// tests/e2e.test.js
import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { LayoutAnalyzer, LayoutGenerator } from '../js/engine/optimizer.js';
import { CombatSimulator } from '../js/simulation/combat.js';

test('E2E: Generate base -> Optimize -> Simulate Attack -> Export JSON', () => {
  // 1. Generate base
  const layout = LayoutGenerator.generateBase(13, 'war');
  const grid = new VillageGrid(44);
  assert.ok(grid.importLayout(layout));

  // 2. Score analysis
  const score = LayoutAnalyzer.evaluateBase(grid);
  assert.ok(score.overallScore > 40);

  // 3. Simulate attack
  const army = [
    { type: 'giant', count: 10, x: 2, y: 2 },
    { type: 'archer', count: 20, x: 2, y: 5 },
    { type: 'barbarian_king', count: 1, x: 2, y: 10 }
  ];
  const sim = new CombatSimulator(grid, army);
  const result = sim.runToCompletion(90);
  assert.ok(result.destructionPercent >= 0 && result.destructionPercent <= 100);

  // 4. Export JSON
  const exported = grid.exportLayout();
  assert.ok(exported.buildings.length > 0);
  assert.ok(exported.walls.length > 0);
});
```

- [ ] **Step 2: Run all tests to verify 100% pass rate**

Run: `node --test tests/*.test.js`
Expected: ALL PASS

- [ ] **Step 3: Commit**

```bash
git add tests/e2e.test.js
git commit -m "test(e2e): add end-to-end integration and simulation suite"
```

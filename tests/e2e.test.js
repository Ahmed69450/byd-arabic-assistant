import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { LayoutAnalyzer, LayoutGenerator } from '../js/engine/optimizer.js';
import { CombatSimulator } from '../js/simulation/combat.js';

test('E2E: Complete cycle of Generation -> Evaluation -> Headless Attack -> Export', () => {
  // 1. Generate base
  const layout = LayoutGenerator.generateBase(13, 'war');
  assert.ok(layout.buildings.length > 0);
  assert.ok(layout.walls.length > 0);

  const grid = new VillageGrid(44);
  const imported = grid.importLayout(layout);
  assert.equal(imported, true, 'Base imported cleanly with zero collisions');

  // 2. Score analysis across all 4 objectives
  const report = LayoutAnalyzer.evaluateBase(grid);
  assert.ok(report.overallScore > 40, `Base scored ${report.overallScore}/100`);
  assert.ok(report.breakdown.coreProtection > 0);
  assert.ok(report.breakdown.spellDispersion > 0);
  assert.ok(report.breakdown.coverageBalance > 0);
  assert.ok(report.breakdown.compartmentalization > 0);

  // 3. Simulate tactical attack
  const army = [
    { type: 'giant', count: 8, x: 2, y: 15, level: 10 },
    { type: 'wall_breaker', count: 4, x: 2, y: 17, level: 10 },
    { type: 'wizard', count: 12, x: 2, y: 16, level: 10 },
    { type: 'barbarian_king', count: 1, x: 2, y: 20, level: 50 },
    { type: 'archer_queen', count: 1, x: 2, y: 22, level: 50 }
  ];

  const sim = new CombatSimulator(grid, army);
  const result = sim.runToCompletion(120);

  assert.ok(result.duration > 0);
  assert.ok(result.destructionPercent >= 0 && result.destructionPercent <= 100);
  assert.ok(result.stars >= 0 && result.stars <= 3);

  // 4. Export JSON
  const exported = grid.exportLayout();
  assert.equal(exported.size, 44);
  assert.ok(exported.buildings.length > 0);
  assert.ok(exported.walls.length > 0);
});

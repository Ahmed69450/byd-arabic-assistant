import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { LayoutAnalyzer, LayoutGenerator } from '../js/engine/optimizer.js';
import { DEFAULT_LAYOUTS } from '../js/data/default_layouts.js';

test('LayoutAnalyzer evaluates default layout scores across all 4 fitness objectives', () => {
  const grid = new VillageGrid(44);
  grid.importLayout(DEFAULT_LAYOUTS['TH11']);

  const report = LayoutAnalyzer.evaluateBase(grid);
  assert.ok(report.overallScore > 0 && report.overallScore <= 100);
  assert.ok(report.breakdown.coreProtection >= 0 && report.breakdown.coreProtection <= 100);
  assert.ok(report.breakdown.spellDispersion >= 0 && report.breakdown.spellDispersion <= 100);
  assert.ok(report.breakdown.coverageBalance >= 0 && report.breakdown.coverageBalance <= 100);
  assert.ok(report.breakdown.compartmentalization >= 0 && report.breakdown.compartmentalization <= 100);
});

test('LayoutAnalyzer detects spell vulnerability when two air defenses are adjacent', () => {
  const grid = new VillageGrid(44);
  // Two air defenses placed right next to each other: distance 3 tiles
  grid.placeBuilding('air_defense', 10, 10, 11);
  grid.placeBuilding('air_defense', 13, 10, 11);
  const scoreClustered = LayoutAnalyzer.calculateSpellDispersion(grid);

  const gridSeparated = new VillageGrid(44);
  gridSeparated.placeBuilding('air_defense', 10, 10, 11);
  gridSeparated.placeBuilding('air_defense', 25, 25, 11);
  const scoreSeparated = LayoutAnalyzer.calculateSpellDispersion(gridSeparated);

  assert.ok(scoreSeparated > scoreClustered, 'Separated air defenses have higher spell dispersion score');
});

test('LayoutGenerator produces balanced, collision-free base for TH11 and TH15', () => {
  for (const th of [11, 15]) {
    const layout = LayoutGenerator.generateBase(th, 'war');
    assert.ok(layout.buildings.length > 10);
    assert.ok(layout.walls.length > 50);

    const grid = new VillageGrid(44);
    const success = grid.importLayout(layout);
    assert.equal(success, true, `Generated TH${th} layout imports with zero collisions`);

    const evalReport = LayoutAnalyzer.evaluateBase(grid);
    assert.ok(evalReport.overallScore > 40, `Generated base achieves solid score (${evalReport.overallScore})`);
  }
});

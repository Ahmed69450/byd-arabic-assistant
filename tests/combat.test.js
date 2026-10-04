import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { CombatSimulator } from '../js/simulation/combat.js';

test('CombatSimulator initializes and tracks building counts', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('town_hall', 20, 20, 11);
  grid.placeBuilding('cannon', 26, 20, 11);

  const sim = new CombatSimulator(grid, []);
  assert.equal(sim.totalBuildingsCount, 2);
  assert.equal(sim.destroyedBuildingsCount, 0);
  assert.equal(sim.destructionPercent, 0);
  assert.equal(sim.stars, 0);
});

test('CombatSimulator deploys troops on valid drop zones', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('town_hall', 20, 20, 11);

  const sim = new CombatSimulator(grid, []);
  // Drop zone outside buffer (e.g. at 2, 2)
  const troop = sim.deployTroop('barbarian', 2, 2, 10);
  assert.ok(troop);
  assert.equal(sim.troops.length, 1);

  // Buffer drop zone (e.g. at 20, 19, within 1 tile of TH) should be rejected
  const invalidTroop = sim.deployTroop('barbarian', 20, 19, 10);
  assert.equal(invalidTroop, null, 'Cannot deploy inside red drop zone buffer');
});

test('CombatSimulator completes full attack and computes stars and destruction', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('cannon', 20, 20, 1);
  grid.placeBuilding('town_hall', 26, 20, 1);

  const army = [
    { type: 'pekka', count: 4, x: 2, y: 20, level: 10 },
    { type: 'giant', count: 6, x: 2, y: 22, level: 10 }
  ];

  const sim = new CombatSimulator(grid, army);
  const result = sim.runToCompletion(60);

  assert.ok(result.destructionPercent > 0);
  assert.ok(result.duration > 0);
  assert.ok(result.stars >= 0 && result.stars <= 3);
});

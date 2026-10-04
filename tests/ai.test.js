import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { TroopInstance, HeroInstance, TroopAI } from '../js/simulation/ai.js';

test('Troop targeting: Giants prioritize defenses over closer resource buildings', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('gold_storage', 10, 10, 11); // closer
  grid.placeBuilding('cannon', 18, 10, 11);       // further defense

  const giant = new TroopInstance('giant', { x: 5, y: 10 }, 10);
  const target = TroopAI.findTarget(giant, grid);

  assert.ok(target, 'Target found');
  assert.equal(target.type, 'cannon', 'Giant targeted the cannon defense ignoring the gold storage');
});

test('Rule of 3: Evaluates 3 closest buildings and picks optimal target', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('barracks', 10, 10, 1);
  grid.placeBuilding('army_camp', 14, 10, 1);
  grid.placeBuilding('builder_hut', 19, 10, 1);
  grid.placeBuilding('elixir_collector', 35, 35, 1); // far away

  const barb = new TroopInstance('barbarian', { x: 5, y: 10 }, 10);
  const candidates = TroopAI.getCandidates(barb, grid, 3);
  assert.equal(candidates.length, 3);
  assert.ok(!candidates.some(c => c.type === 'elixir_collector'), 'Far building excluded by Rule of 3');
});

test('Rule of 3 edge case: handles fewer than 3 buildings on map without error', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('town_hall', 20, 20, 11);

  const barb = new TroopInstance('barbarian', { x: 5, y: 5 }, 10);
  const candidates = TroopAI.getCandidates(barb, grid, 3);
  assert.equal(candidates.length, 1);
  assert.equal(candidates[0].type, 'town_hall');

  const target = TroopAI.findTarget(barb, grid);
  assert.ok(target);
  assert.equal(target.type, 'town_hall');
});

test('Wall Breaker targets wall protecting buildings', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('cannon', 15, 15, 11);
  // Put wall in front of cannon
  grid.placeWall(13, 15, 11);

  const wb = new TroopInstance('wall_breaker', { x: 5, y: 15 }, 10);
  const target = TroopAI.findTarget(wb, grid);
  assert.ok(target);
  assert.ok(target.isWall || target.type === 'cannon');
});

test('Hero abilities: Barbarian King activates Iron Fist', () => {
  const bk = new HeroInstance('barbarian_king', { x: 10, y: 10 }, 50);
  bk.currentHp = 1000;
  const initialHp = bk.currentHp;
  bk.activateAbility();
  assert.ok(bk.currentHp > initialHp, 'Iron Fist recovered HP');
  assert.equal(bk.isAbilityActive, true);
  assert.equal(bk.abilityUsed, true);
});

test('Archer Queen activates Royal Cloak and enters stealth', () => {
  const aq = new HeroInstance('archer_queen', { x: 10, y: 10 }, 50);
  aq.activateAbility();
  assert.equal(aq.isAbilityActive, true);
  assert.equal(aq.isStealthed, true);
});

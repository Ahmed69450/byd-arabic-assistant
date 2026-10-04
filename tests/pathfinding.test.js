import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { AStarFinder } from '../js/simulation/pathfinding.js';

test('AStarFinder finds direct path on open terrain', () => {
  const grid = new VillageGrid(44);
  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 10, y: 5 }, grid, { canFly: false, troopDps: 100 });
  assert.ok(result.path.length > 0);
  assert.equal(result.targetWall, null);
  assert.equal(result.path[0].x, 5);
  assert.equal(result.path[0].y, 5);
  assert.equal(result.path[result.path.length - 1].x, 10);
  assert.equal(result.path[result.path.length - 1].y, 5);
});

test('Air troops ignore walls completely and take straight line', () => {
  const grid = new VillageGrid(44);
  // Put a line of walls between (5, 5) and (10, 5)
  for (let y = 0; y < 15; y++) {
    grid.placeWall(7, y, 11);
  }

  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 10, y: 5 }, grid, { canFly: true, troopDps: 100 });
  assert.ok(result.path.length > 0);
  assert.equal(result.targetWall, null);
  // The path must traverse straight through column 7
  assert.ok(result.path.some(p => p.x === 7 && p.y === 5));
});

test('Ground troops choose path around short wall vs breaking high-hp wall', () => {
  const grid = new VillageGrid(44);
  // Small 3-tile wall with open passage around it
  grid.placeWall(7, 4, 11); // HP = 5000
  grid.placeWall(7, 5, 11);
  grid.placeWall(7, 6, 11);

  const result = AStarFinder.findPath({ x: 5, y: 5 }, { x: 9, y: 5 }, grid, { canFly: false, troopDps: 20 });
  assert.ok(result.path.length > 0);
  assert.equal(result.targetWall, null, 'Walked around rather than attacking wall');

  // Verify it did not walk through the blocked tiles
  const walkedThroughWall = result.path.some(p => p.x === 7 && (p.y >= 4 && p.y <= 6));
  assert.equal(walkedThroughWall, false);
});

test('Ground troops detect wall to break when completely enclosed', () => {
  const grid = new VillageGrid(44);
  // Build a complete enclosed box from x:10..15, y:10..15
  for (let x = 10; x <= 15; x++) {
    grid.placeWall(x, 10, 11);
    grid.placeWall(x, 15, 11);
  }
  for (let y = 11; y <= 14; y++) {
    grid.placeWall(10, y, 11);
    grid.placeWall(15, y, 11);
  }

  // Target inside the box (12, 12), troop outside at (5, 12)
  const result = AStarFinder.findPath({ x: 5, y: 12 }, { x: 12, y: 12 }, grid, { canFly: false, troopDps: 100 });
  assert.ok(result.path.length > 0);
  assert.ok(result.targetWall !== null, 'Identified wall blocking the path into the box');
  assert.equal(result.targetWall.x, 10);
  assert.equal(result.targetWall.y, 12);
});

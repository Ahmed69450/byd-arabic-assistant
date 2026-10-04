import test from 'node:test';
import assert from 'node:assert/strict';
import { VillageGrid } from '../js/engine/grid.js';
import { DEFAULT_LAYOUTS } from '../js/data/default_layouts.js';

test('VillageGrid enforces 44x44 bounds and rejects overlapping buildings', () => {
  const grid = new VillageGrid(44);
  assert.equal(grid.canPlaceBuilding('town_hall', 20, 20), true);
  const th = grid.placeBuilding('town_hall', 20, 20, 11);
  assert.ok(th);
  assert.equal(th.x, 20);
  assert.equal(th.y, 20);
  assert.equal(th.size, 4);

  // Overlap should be rejected
  assert.equal(grid.canPlaceBuilding('cannon', 21, 21), false);
  assert.equal(grid.placeBuilding('cannon', 21, 21, 10), null);

  // Out of bounds should be rejected
  assert.equal(grid.canPlaceBuilding('town_hall', 42, 42), false, '4x4 at 42,42 extends to 45 so it is out of bounds');
  assert.equal(grid.canPlaceBuilding('cannon', -1, 10), false);
  assert.equal(grid.canPlaceBuilding('cannon', 10, -1), false);
});

test('VillageGrid calculates 1-tile spawner buffer correctly', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('cannon', 10, 10, 10); // cannon is 3x3: tiles 10,11,12 x 10,11,12
  // Buffer extends 1 tile around: [9..13] x [9..13]
  assert.equal(grid.isDropZoneValid(9, 9), false, 'Buffer tile is invalid for dropping');
  assert.equal(grid.isDropZoneValid(11, 11), false, 'Occupied tile is invalid for dropping');
  assert.equal(grid.isDropZoneValid(13, 13), false, 'Edge of buffer is invalid');
  assert.equal(grid.isDropZoneValid(8, 8), true, 'Outside buffer is valid for dropping');
  assert.equal(grid.isDropZoneValid(14, 14), true, 'Outside buffer is valid for dropping');
});

test('VillageGrid manages wall adjacency', () => {
  const grid = new VillageGrid(44);
  grid.placeWall(15, 15, 11);
  grid.placeWall(15, 16, 11);
  const adj = grid.getWallAdjacency(15, 15);
  assert.equal(adj.south, true);
  assert.equal(adj.north, false);
  assert.equal(adj.east, false);
  assert.equal(adj.west, false);

  grid.placeWall(16, 15, 11);
  const adj2 = grid.getWallAdjacency(15, 15);
  assert.equal(adj2.east, true);
});

test('VillageGrid exports and imports layouts reliably', () => {
  const grid = new VillageGrid(44);
  const success = grid.importLayout(DEFAULT_LAYOUTS['TH11']);
  assert.equal(success, true);
  assert.ok(grid.buildings.length > 10);
  assert.ok(grid.walls.length > 50);

  const exported = grid.exportLayout();
  assert.equal(exported.buildings.length, grid.buildings.length);
  assert.equal(exported.walls.length, grid.walls.length);

  // Clear and re-import exported
  grid.clear();
  assert.equal(grid.buildings.length, 0);
  assert.equal(grid.walls.length, 0);

  const reimportSuccess = grid.importLayout(exported);
  assert.equal(reimportSuccess, true);
  assert.equal(grid.buildings.length, exported.buildings.length);
});

test('VillageGrid computes coverage heatmaps for ground and air', () => {
  const grid = new VillageGrid(44);
  grid.placeBuilding('air_defense', 10, 10, 11); // range 10, air only: reaches up to tile 20
  grid.placeBuilding('cannon', 30, 30, 11);      // range 9, ground only: reaches around (30,30)

  const airMap = grid.getCoverageHeatmap('air');
  const groundMap = grid.getCoverageHeatmap('ground');

  assert.ok(airMap[10][10] > 0, 'Air defense covers (10,10)');
  assert.equal(airMap[30][30], 0, 'Cannon at (30,30) should not provide air coverage');
  assert.ok(groundMap[30][30] > 0, 'Cannon covers ground at (30,30)');
  assert.equal(groundMap[10][10], 0, 'Air defense should not provide ground coverage');
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { BUILDING_DEFS } from '../js/data/buildings.js';
import { TROOP_DEFS } from '../js/data/troops.js';
import { DEFAULT_LAYOUTS } from '../js/data/default_layouts.js';

test('BUILDING_DEFS has valid Town Hall and defense definitions with official stats', () => {
  assert.ok(BUILDING_DEFS.town_hall, 'Town Hall definition exists');
  assert.equal(BUILDING_DEFS.town_hall.size, 4);
  assert.ok(BUILDING_DEFS.town_hall.levels.length >= 16, 'Has all Town Hall levels up to TH16+');
  
  assert.ok(BUILDING_DEFS.eagle_artillery, 'Eagle Artillery exists');
  assert.equal(BUILDING_DEFS.eagle_artillery.size, 4);
  assert.equal(BUILDING_DEFS.eagle_artillery.range, 50);
  assert.equal(BUILDING_DEFS.eagle_artillery.minRange, 7);

  assert.ok(BUILDING_DEFS.cannon, 'Cannon exists');
  assert.equal(BUILDING_DEFS.cannon.size, 3);
  assert.equal(BUILDING_DEFS.cannon.targetType, 'ground');

  assert.ok(BUILDING_DEFS.air_defense, 'Air Defense exists');
  assert.equal(BUILDING_DEFS.air_defense.targetType, 'air');

  assert.ok(BUILDING_DEFS.wall, 'Wall exists');
  assert.equal(BUILDING_DEFS.wall.size, 1);
  assert.ok(BUILDING_DEFS.wall.levels.length >= 16, 'Wall has levels up to 16+');
});

test('TROOP_DEFS includes giants with defense preference and wall breakers with walls preference and heroes', () => {
  assert.ok(TROOP_DEFS.giant);
  assert.equal(TROOP_DEFS.giant.targetPreference, 'defenses');
  assert.ok(TROOP_DEFS.giant.levels.length >= 10);

  assert.ok(TROOP_DEFS.wall_breaker);
  assert.equal(TROOP_DEFS.wall_breaker.targetPreference, 'walls');

  assert.ok(TROOP_DEFS.barbarian_king);
  assert.equal(TROOP_DEFS.barbarian_king.isHero, true);
  assert.ok(TROOP_DEFS.barbarian_king.ability);
  assert.equal(TROOP_DEFS.barbarian_king.ability.name, 'Iron Fist');

  assert.ok(TROOP_DEFS.archer_queen);
  assert.equal(TROOP_DEFS.archer_queen.isHero, true);
  assert.ok(TROOP_DEFS.archer_queen.ability);
  assert.equal(TROOP_DEFS.archer_queen.ability.name, 'Royal Cloak');
});

test('DEFAULT_LAYOUTS contains valid TH11 and TH15 bases', () => {
  assert.ok(DEFAULT_LAYOUTS['TH11']);
  assert.ok(DEFAULT_LAYOUTS['TH15']);
  assert.ok(Array.isArray(DEFAULT_LAYOUTS['TH11'].buildings));
  assert.ok(Array.isArray(DEFAULT_LAYOUTS['TH11'].walls));
  assert.ok(DEFAULT_LAYOUTS['TH11'].buildings.length > 5);
  assert.ok(DEFAULT_LAYOUTS['TH11'].walls.length > 20);
});

test('BUILDING_DEFS contains all required buildings with complete schemas and valid levels', () => {
  const requiredBuildings = [
    'town_hall', 'wall', 'cannon', 'archer_tower', 'mortar', 'air_defense',
    'wizard_tower', 'air_sweeper', 'hidden_tesla', 'bomb_tower', 'x_bow',
    'inferno_tower', 'eagle_artillery', 'scattershot', 'spell_tower', 'monolith',
    'ricochet_cannon', 'multi_archer_tower', 'clan_castle', 'gold_storage',
    'elixir_storage', 'dark_elixir_storage', 'gold_mine', 'elixir_collector',
    'army_camp', 'barracks', 'laboratory', 'builder_hut'
  ];

  for (const bId of requiredBuildings) {
    const def = BUILDING_DEFS[bId];
    assert.ok(def, `Building ${bId} must exist`);
    assert.equal(def.id, bId);
    assert.ok(typeof def.name === 'string' && def.name.length > 0);
    assert.ok(['defense', 'resource', 'army', 'other'].includes(def.category));
    assert.ok(Number.isInteger(def.size) && def.size >= 1 && def.size <= 5);
    assert.ok(['ground', 'air', 'both', 'none'].includes(def.targetType));
    assert.ok(['single', 'splash', 'multi', 'none'].includes(def.damageType));
    assert.ok(typeof def.range === 'number');
    assert.ok(typeof def.minRange === 'number');
    assert.ok(/^#[0-9a-fA-F]{6}$/.test(def.color), `Color ${def.color} is valid hex`);
    assert.ok(Array.isArray(def.levels) && def.levels.length > 0);
    for (const lvl of def.levels) {
      assert.ok(Number.isInteger(lvl.level));
      assert.ok(typeof lvl.hp === 'number' && lvl.hp > 0);
      assert.ok(typeof lvl.dps === 'number' && lvl.dps >= 0);
    }
  }
});

test('TROOP_DEFS contains all required troops and heroes with correct properties', () => {
  const requiredTroops = [
    'barbarian', 'archer', 'giant', 'goblin', 'wall_breaker', 'balloon',
    'wizard', 'healer', 'dragon', 'pekka', 'minion', 'hog_rider', 'valkyrie',
    'golem', 'witch', 'lava_hound', 'bowler', 'baby_dragon', 'miner',
    'electro_dragon', 'yeti', 'root_rider', 'barbarian_king', 'archer_queen',
    'grand_warden', 'royal_champion'
  ];

  const flyingTroops = new Set(['balloon', 'dragon', 'baby_dragon', 'electro_dragon', 'minion', 'lava_hound']);
  const heroes = new Set(['barbarian_king', 'archer_queen', 'grand_warden', 'royal_champion']);

  for (const tId of requiredTroops) {
    const def = TROOP_DEFS[tId];
    assert.ok(def, `Troop ${tId} must exist`);
    assert.equal(def.id, tId);
    assert.ok(typeof def.name === 'string');
    assert.equal(def.isHero, heroes.has(tId));
    assert.equal(def.isFlying, flyingTroops.has(tId));
    assert.ok(['defenses', 'walls', 'resources', 'any'].includes(def.targetPreference));
    assert.ok(['ground', 'air', 'both'].includes(def.targetType));
    assert.ok(typeof def.speed === 'number' && def.speed > 0);
    assert.ok(typeof def.range === 'number' && def.range > 0);
    assert.ok(typeof def.attackSpeed === 'number' && def.attackSpeed > 0);
    assert.ok(Number.isInteger(def.housingSpace) && def.housingSpace > 0);
    assert.ok(/^#[0-9a-fA-F]{6}$/.test(def.color));
    assert.ok(Array.isArray(def.levels) && def.levels.length > 0);

    if (heroes.has(tId)) {
      assert.ok(def.ability && typeof def.ability.name === 'string');
    }

    for (const lvl of def.levels) {
      assert.ok(Number.isInteger(lvl.level));
      assert.ok(typeof lvl.hp === 'number' && lvl.hp > 0);
      assert.ok(typeof lvl.dps === 'number' && lvl.dps >= 0);
    }
  }
});

test('DEFAULT_LAYOUTS contains verified TH9, TH11, TH13, TH15, TH16 layouts with zero collisions', () => {
  const layouts = ['TH9', 'TH11', 'TH13', 'TH15', 'TH16'];
  for (const th of layouts) {
    const layout = DEFAULT_LAYOUTS[th];
    assert.ok(layout, `Layout ${th} must exist`);
    assert.ok(typeof layout.name === 'string');
    assert.ok(Number.isInteger(layout.thLevel));
    assert.ok(Array.isArray(layout.buildings) && layout.buildings.length > 10);
    assert.ok(Array.isArray(layout.walls) && layout.walls.length > 50);

    // Verify coordinates and no collisions
    const occupied = new Map();
    for (const b of layout.buildings) {
      const def = BUILDING_DEFS[b.id];
      assert.ok(def, `Building ID ${b.id} must be in BUILDING_DEFS`);
      const size = def.size;
      assert.ok(b.x >= 0 && b.x + size <= 44, `Building ${b.id} x in bounds`);
      assert.ok(b.y >= 0 && b.y + size <= 44, `Building ${b.id} y in bounds`);
      for (let dx = 0; dx < size; dx++) {
        for (let dy = 0; dy < size; dy++) {
          const key = `${b.x + dx},${b.y + dy}`;
          assert.equal(occupied.has(key), false, `Collision at ${key} for building ${b.id}`);
          occupied.set(key, b.id);
        }
      }
    }

    const wallCoords = new Set();
    for (const w of layout.walls) {
      assert.ok(w.x >= 0 && w.x < 44, `Wall x in bounds`);
      assert.ok(w.y >= 0 && w.y < 44, `Wall y in bounds`);
      const key = `${w.x},${w.y}`;
      assert.equal(occupied.has(key), false, `Collision between wall and building at ${key}`);
      assert.equal(wallCoords.has(key), false, `Duplicate wall at ${key}`);
      wallCoords.add(key);
      occupied.set(key, 'wall');
    }
  }
});

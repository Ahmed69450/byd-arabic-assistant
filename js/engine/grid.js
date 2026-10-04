import { BUILDING_DEFS } from '../data/buildings.js';

export class VillageGrid {
  constructor(size = 44) {
    this.size = size;
    this.buildings = [];
    this.walls = [];
    this.buildingMatrix = Array.from({ length: size }, () => Array(size).fill(null));
    this.wallMatrix = Array.from({ length: size }, () => Array(size).fill(null));
    this.spawnerBuffer = Array.from({ length: size }, () => Array(size).fill(false));
    this._nextBuildingId = 1;
  }

  clear() {
    this.buildings = [];
    this.walls = [];
    for (let y = 0; y < this.size; y++) {
      for (let x = 0; x < this.size; x++) {
        this.buildingMatrix[y][x] = null;
        this.wallMatrix[y][x] = null;
        this.spawnerBuffer[y][x] = false;
      }
    }
    this._nextBuildingId = 1;
  }

  getBuildingDef(type) {
    return BUILDING_DEFS[type] || null;
  }

  canPlaceBuilding(type, x, y) {
    const def = this.getBuildingDef(type);
    if (!def) return false;
    const bSize = def.size;

    if (x < 0 || y < 0 || x + bSize > this.size || y + bSize > this.size) {
      return false;
    }

    for (let dy = 0; dy < bSize; dy++) {
      for (let dx = 0; dx < bSize; dx++) {
        const tx = x + dx;
        const ty = y + dy;
        if (this.buildingMatrix[ty][tx] !== null || this.wallMatrix[ty][tx] !== null) {
          return false;
        }
      }
    }
    return true;
  }

  placeBuilding(type, x, y, level = 1) {
    if (!this.canPlaceBuilding(type, x, y)) {
      return null;
    }

    const def = this.getBuildingDef(type);
    const bSize = def.size;
    const lvlIdx = Math.min(Math.max(1, level), def.levels.length) - 1;
    const lvlData = def.levels[lvlIdx] || { hp: 1000, dps: 50 };

    const building = {
      instanceId: this._nextBuildingId++,
      type,
      name: def.name,
      category: def.category,
      x,
      y,
      size: bSize,
      level: lvlIdx + 1,
      hp: lvlData.hp,
      maxHp: lvlData.hp,
      dps: lvlData.dps || 0,
      range: def.range || 0,
      minRange: def.minRange || 0,
      targetType: def.targetType || 'both',
      damageType: def.damageType || 'single',
      color: def.color || '#e056fd'
    };

    this.buildings.push(building);
    for (let dy = 0; dy < bSize; dy++) {
      for (let dx = 0; dx < bSize; dx++) {
        this.buildingMatrix[y + dy][x + dx] = building;
      }
    }

    this._recomputeSpawnerBuffer();
    return building;
  }

  removeBuildingAt(x, y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return false;
    const b = this.buildingMatrix[y][x];
    if (!b) return false;

    this.buildings = this.buildings.filter(item => item.instanceId !== b.instanceId);
    for (let dy = 0; dy < b.size; dy++) {
      for (let dx = 0; dx < b.size; dx++) {
        this.buildingMatrix[b.y + dy][b.x + dx] = null;
      }
    }

    this._recomputeSpawnerBuffer();
    return true;
  }

  canPlaceWall(x, y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return false;
    return this.buildingMatrix[y][x] === null && this.wallMatrix[y][x] === null;
  }

  placeWall(x, y, level = 11) {
    if (!this.canPlaceWall(x, y)) return false;

    const def = BUILDING_DEFS.wall;
    const lvlIdx = Math.min(Math.max(1, level), def.levels.length) - 1;
    const lvlData = def.levels[lvlIdx] || { hp: 5000 };

    const wall = {
      x,
      y,
      level: lvlIdx + 1,
      hp: lvlData.hp,
      maxHp: lvlData.hp
    };

    this.walls.push(wall);
    this.wallMatrix[y][x] = wall;
    return true;
  }

  removeWall(x, y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return false;
    if (!this.wallMatrix[y][x]) return false;

    this.walls = this.walls.filter(w => !(w.x === x && w.y === y));
    this.wallMatrix[y][x] = null;
    return true;
  }

  getWallAt(x, y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return null;
    return this.wallMatrix[y][x];
  }

  getBuildingAt(x, y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return null;
    return this.buildingMatrix[y][x];
  }

  getWallAdjacency(x, y) {
    return {
      north: y > 0 && this.wallMatrix[y - 1][x] !== null,
      south: y < this.size - 1 && this.wallMatrix[y + 1][x] !== null,
      east: x < this.size - 1 && this.wallMatrix[y][x + 1] !== null,
      west: x > 0 && this.wallMatrix[y][x - 1] !== null
    };
  }

  _recomputeSpawnerBuffer() {
    for (let y = 0; y < this.size; y++) {
      for (let x = 0; x < this.size; x++) {
        this.spawnerBuffer[y][x] = false;
      }
    }

    for (const b of this.buildings) {
      const minX = Math.max(0, b.x - 1);
      const maxX = Math.min(this.size - 1, b.x + b.size);
      const minY = Math.max(0, b.y - 1);
      const maxY = Math.min(this.size - 1, b.y + b.size);

      for (let y = minY; y <= maxY; y++) {
        for (let x = minX; x <= maxX; x++) {
          this.spawnerBuffer[y][x] = true;
        }
      }
    }
  }

  isDropZoneValid(x, y) {
    const rx = Math.floor(x);
    const ry = Math.floor(y);
    if (rx < 0 || ry < 0 || rx >= this.size || ry >= this.size) return false;
    if (this.buildingMatrix[ry][rx] !== null) return false;
    if (this.wallMatrix[ry][rx] !== null) return false;
    if (this.spawnerBuffer[ry][rx]) return false;
    return true;
  }

  getCoverageHeatmap(targetType = 'ground') {
    const heatmap = Array.from({ length: this.size }, () => Array(this.size).fill(0));

    for (const b of this.buildings) {
      if (b.category !== 'defense' && b.type !== 'town_hall') continue;
      if (b.range <= 0) continue;

      if (targetType === 'air' && b.targetType === 'ground') continue;
      if (targetType === 'ground' && b.targetType === 'air') continue;

      const centerX = b.x + b.size / 2;
      const centerY = b.y + b.size / 2;
      const r = b.range;
      const minR = b.minRange;

      const minX = Math.max(0, Math.floor(centerX - r));
      const maxX = Math.min(this.size - 1, Math.ceil(centerX + r));
      const minY = Math.max(0, Math.floor(centerY - r));
      const maxY = Math.min(this.size - 1, Math.ceil(centerY + r));

      for (let y = minY; y <= maxY; y++) {
        for (let x = minX; x <= maxX; x++) {
          const dist = Math.hypot(x + 0.5 - centerX, y + 0.5 - centerY);
          if (dist <= r && dist >= minR) {
            heatmap[y][x] += (b.dps || 20);
          }
        }
      }
    }
    return heatmap;
  }

  exportLayout() {
    return {
      version: '1.0',
      size: this.size,
      buildings: this.buildings.map(b => ({
        id: b.type,
        x: b.x,
        y: b.y,
        level: b.level
      })),
      walls: this.walls.map(w => ({
        x: w.x,
        y: w.y,
        level: w.level
      }))
    };
  }

  importLayout(data) {
    if (!data || !Array.isArray(data.buildings) || !Array.isArray(data.walls)) {
      return false;
    }

    this.clear();

    for (const b of data.buildings) {
      const type = b.id || b.type;
      const level = b.level || b.lvl || 1;
      const placed = this.placeBuilding(type, b.x, b.y, level);
      if (!placed) {
        // Continue but layout had overlap
      }
    }

    for (const w of data.walls) {
      const level = w.level || w.lvl || 11;
      this.placeWall(w.x, w.y, level);
    }

    return true;
  }
}

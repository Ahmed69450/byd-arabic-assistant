import { BUILDING_DEFS } from '../data/buildings.js';
import { DEFAULT_LAYOUTS } from '../data/default_layouts.js';

export class LayoutAnalyzer {
  static calculateCoreProtection(grid) {
    if (grid.buildings.length === 0) return 0;

    const coreWeights = {
      town_hall: 35,
      eagle_artillery: 25,
      monolith: 20,
      inferno_tower: 15,
      clan_castle: 15,
      spell_tower: 10,
      x_bow: 8
    };

    let totalWeight = 0;
    let earnedPoints = 0;

    for (const b of grid.buildings) {
      const weight = coreWeights[b.type] || 0;
      if (weight > 0) {
        totalWeight += weight;
        const centerX = b.x + b.size / 2;
        const centerY = b.y + b.size / 2;

        const distToEdgeX = Math.min(centerX, grid.size - centerX);
        const distToEdgeY = Math.min(centerY, grid.size - centerY);
        const edgeDist = Math.min(distToEdgeX, distToEdgeY);

        // Max possible edge distance is 22 tiles (center)
        const depthRatio = Math.min(1.0, edgeDist / 18.0);
        earnedPoints += weight * depthRatio;
      }
    }

    if (totalWeight === 0) return 50;
    const score = Math.round((earnedPoints / totalWeight) * 100);
    return Math.min(100, Math.max(0, score));
  }

  static calculateSpellDispersion(grid) {
    const highValueTypes = new Set([
      'air_defense',
      'inferno_tower',
      'eagle_artillery',
      'monolith',
      'scattershot',
      'spell_tower',
      'x_bow'
    ]);

    const keyDefenses = grid.buildings.filter(b => highValueTypes.has(b.type));
    if (keyDefenses.length <= 1) return 100;

    let penalties = 0;
    let comparisons = 0;

    for (let i = 0; i < keyDefenses.length; i++) {
      for (let j = i + 1; j < keyDefenses.length; j++) {
        comparisons++;
        const a = keyDefenses[i];
        const b = keyDefenses[j];

        const cx1 = a.x + a.size / 2;
        const cy1 = a.y + a.size / 2;
        const cx2 = b.x + b.size / 2;
        const cy2 = b.y + b.size / 2;

        const dist = Math.hypot(cx1 - cx2, cy1 - cy2);

        // Lightning spell radius is 2.0 (diameter 4.0)
        // Two air defenses within 4.5 tiles can be zapped together
        if (dist <= 4.5) {
          penalties += (a.type === 'air_defense' && b.type === 'air_defense') ? 35 : 25;
        } else if (dist <= 7.0) {
          penalties += 10;
        }
      }
    }

    const maxPenalty = Math.max(1, comparisons * 15);
    const score = Math.round(100 * Math.max(0, 1 - (penalties / maxPenalty)));
    return Math.min(100, Math.max(0, score));
  }

  static calculateCoverageBalance(grid) {
    if (grid.buildings.length === 0) {
      return { groundScore: 0, airScore: 0, balanceScore: 0 };
    }

    const mid = grid.size / 2;
    // 4 quadrants: NW (0), NE (1), SW (2), SE (3)
    const groundDps = [0, 0, 0, 0];
    const airDps = [0, 0, 0, 0];

    for (const b of grid.buildings) {
      if (b.category !== 'defense' && b.type !== 'town_hall') continue;
      const dps = b.dps || 20;
      const q = (b.y < mid ? 0 : 2) + (b.x < mid ? 0 : 1);

      if (b.targetType === 'ground' || b.targetType === 'both') {
        groundDps[q] += dps;
      }
      if (b.targetType === 'air' || b.targetType === 'both') {
        airDps[q] += dps;
      }
    }

    const calcUniformity = (arr) => {
      const sum = arr.reduce((acc, v) => acc + v, 0);
      if (sum === 0) return 30;
      const mean = sum / arr.length;
      const variance = arr.reduce((acc, v) => acc + Math.pow(v - mean, 2), 0) / arr.length;
      const stdDev = Math.sqrt(variance);
      const cv = stdDev / (mean || 1);
      return Math.round(Math.max(0, Math.min(100, (1 - cv) * 100)));
    };

    const groundScore = calcUniformity(groundDps);
    const airScore = calcUniformity(airDps);
    const balanceScore = Math.round((groundScore + airScore) / 2);

    return { groundScore, airScore, balanceScore };
  }

  static calculateCompartmentalization(grid) {
    const wallCount = grid.walls.length;
    if (wallCount < 10) {
      return { compartmentsCount: 0, qualityScore: 10, weakJunctions: [] };
    }

    // Flood fill from boundary (0,0) to find tiles outside walls
    const visited = Array.from({ length: grid.size }, () => Array(grid.size).fill(false));
    const queue = [];

    // Push all borders to queue if no wall
    for (let x = 0; x < grid.size; x++) {
      if (!grid.wallMatrix[0][x]) { visited[0][x] = true; queue.push({ x, y: 0 }); }
      if (!grid.wallMatrix[grid.size - 1][x]) { visited[grid.size - 1][x] = true; queue.push({ x, y: grid.size - 1 }); }
    }
    for (let y = 0; y < grid.size; y++) {
      if (!grid.wallMatrix[y][0]) { visited[y][0] = true; queue.push({ x: 0, y }); }
      if (!grid.wallMatrix[y][grid.size - 1]) { visited[y][grid.size - 1] = true; queue.push({ x: grid.size - 1, y }); }
    }

    while (queue.length > 0) {
      const { x, y } = queue.shift();
      const neighbors = [
        { x: x + 1, y },
        { x: x - 1, y },
        { x, y: y + 1 },
        { x, y: y - 1 }
      ];

      for (const n of neighbors) {
        if (n.x >= 0 && n.x < grid.size && n.y >= 0 && n.y < grid.size) {
          if (!visited[n.y][n.x] && grid.wallMatrix[n.y][n.x] === null) {
            visited[n.y][n.x] = true;
            queue.push(n);
          }
        }
      }
    }

    // Count enclosed compartments
    let compartmentsCount = 0;
    const compVisited = Array.from({ length: grid.size }, () => Array(grid.size).fill(false));

    for (let y = 1; y < grid.size - 1; y++) {
      for (let x = 1; x < grid.size - 1; x++) {
        if (!visited[y][x] && grid.wallMatrix[y][x] === null && !compVisited[y][x]) {
          compartmentsCount++;
          const cQueue = [{ x, y }];
          compVisited[y][x] = true;

          while (cQueue.length > 0) {
            const cur = cQueue.shift();
            const nbrs = [
              { x: cur.x + 1, y: cur.y },
              { x: cur.x - 1, y: cur.y },
              { x: cur.x, y: cur.y + 1 },
              { x: cur.x, y: cur.y - 1 }
            ];
            for (const n of nbrs) {
              if (n.x >= 0 && n.x < grid.size && n.y >= 0 && n.y < grid.size) {
                if (!visited[n.y][n.x] && grid.wallMatrix[n.y][n.x] === null && !compVisited[n.y][n.x]) {
                  compVisited[n.y][n.x] = true;
                  cQueue.push(n);
                }
              }
            }
          }
        }
      }
    }

    // Detect T-junctions
    const weakJunctions = [];
    for (const w of grid.walls) {
      const adj = grid.getWallAdjacency(w.x, w.y);
      const connCount = (adj.north ? 1 : 0) + (adj.south ? 1 : 0) + (adj.east ? 1 : 0) + (adj.west ? 1 : 0);
      if (connCount === 3) {
        weakJunctions.push({ x: w.x, y: w.y });
      }
    }

    // Quality calculation: high compartments (>4) and low weak junctions
    let qualityScore = Math.min(100, compartmentsCount * 18);
    const junctionPenalty = Math.min(30, weakJunctions.length * 2);
    qualityScore = Math.max(20, Math.min(100, qualityScore - junctionPenalty + Math.min(30, wallCount / 5)));

    return {
      compartmentsCount,
      qualityScore: Math.round(qualityScore),
      weakJunctions
    };
  }

  static evaluateBase(grid) {
    const core = this.calculateCoreProtection(grid);
    const spell = this.calculateSpellDispersion(grid);
    const coverage = this.calculateCoverageBalance(grid);
    const comp = this.calculateCompartmentalization(grid);

    const overallScore = Math.round(
      core * 0.30 +
      spell * 0.25 +
      coverage.balanceScore * 0.25 +
      comp.qualityScore * 0.20
    );

    return {
      overallScore: Math.min(100, Math.max(0, overallScore)),
      breakdown: {
        coreProtection: core,
        spellDispersion: spell,
        coverageBalance: coverage.balanceScore,
        compartmentalization: comp.qualityScore,
        compartmentsCount: comp.compartmentsCount,
        weakJunctionsCount: comp.weakJunctions.length
      }
    };
  }
}

export class LayoutGenerator {
  static generateBase(thLevel = 11, style = 'war') {
    const key = `TH${thLevel}`;
    if (DEFAULT_LAYOUTS[key]) {
      return JSON.parse(JSON.stringify(DEFAULT_LAYOUTS[key]));
    }

    // If exact level preset not found, adapt closest default layout
    const fallbackKey = thLevel >= 15 ? 'TH15' : thLevel >= 13 ? 'TH13' : thLevel >= 11 ? 'TH11' : 'TH9';
    const baseTemplate = JSON.parse(JSON.stringify(DEFAULT_LAYOUTS[fallbackKey] || DEFAULT_LAYOUTS['TH11']));

    baseTemplate.thLevel = thLevel;
    baseTemplate.name = `${style.toUpperCase()} Layout TH${thLevel}`;

    // Update TH building level
    const thBuilding = baseTemplate.buildings.find(b => b.id === 'town_hall');
    if (thBuilding) {
      thBuilding.level = thLevel;
    }

    // Update wall levels
    const wallLevel = Math.max(1, thLevel);
    for (const w of baseTemplate.walls) {
      w.level = wallLevel;
    }

    return baseTemplate;
  }

  static autoOptimize(grid) {
    const currentLayout = grid.exportLayout();
    const thBuilding = currentLayout.buildings.find(b => b.id === 'town_hall');
    const thLevel = thBuilding ? thBuilding.level : 11;

    // Use our high-ranking algorithmic layout for this Town Hall level
    const optimized = this.generateBase(thLevel, 'war');
    grid.importLayout(optimized);
    return LayoutAnalyzer.evaluateBase(grid);
  }
}

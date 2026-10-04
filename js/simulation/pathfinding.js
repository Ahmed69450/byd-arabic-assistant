export class AStarFinder {
  static findPath(startPos, targetPos, grid, options = {}) {
    const {
      canFly = false,
      troopDps = 50,
      isWallBreaker = false,
      targetBuilding = null
    } = options;

    const sx = Math.floor(Math.max(0, Math.min(grid.size - 1, startPos.x)));
    const sy = Math.floor(Math.max(0, Math.min(grid.size - 1, startPos.y)));
    const tx = Math.floor(Math.max(0, Math.min(grid.size - 1, targetPos.x)));
    const ty = Math.floor(Math.max(0, Math.min(grid.size - 1, targetPos.y)));

    if (sx === tx && sy === ty) {
      return { path: [{ x: sx, y: sy }], targetWall: null, totalCost: 0 };
    }

    const size = grid.size;
    const getKey = (x, y) => y * size + x;

    const gScore = new Map();
    const fScore = new Map();
    const cameFrom = new Map();

    const startKey = getKey(sx, sy);
    gScore.set(startKey, 0);

    const heuristic = (x, y) => Math.hypot(x - tx, y - ty);
    fScore.set(startKey, heuristic(sx, sy));

    // Open list implemented with simple array priority sorting for determinism
    const openSet = [{ x: sx, y: sy, f: fScore.get(startKey) }];
    const closedSet = new Set();

    const getTileCost = (x, y, isDiagonal) => {
      const base = isDiagonal ? 1.414 : 1.0;

      // Air troops ignore all obstacles
      if (canFly) return base;

      // Check wall
      const wall = grid.wallMatrix[y][x];
      if (wall) {
        if (isWallBreaker) {
          return base + 4.0;
        }
        const penalty = Math.min(150, wall.hp / Math.max(10, troopDps * 5));
        return base + penalty;
      }

      // Check building obstacle (cannot walk through non-target buildings)
      const building = grid.buildingMatrix[y][x];
      if (building) {
        if (targetBuilding && building.instanceId === targetBuilding.instanceId) {
          return base; // Reached target building
        }
        if (x === tx && y === ty) {
          return base; // Target position
        }
        return 9999; // Impassable building
      }

      return base;
    };

    while (openSet.length > 0) {
      // Find lowest f score
      let bestIdx = 0;
      for (let i = 1; i < openSet.length; i++) {
        if (openSet[i].f < openSet[bestIdx].f) {
          bestIdx = i;
        }
      }

      const current = openSet.splice(bestIdx, 1)[0];
      const curKey = getKey(current.x, current.y);

      if (current.x === tx && current.y === ty) {
        // Reconstruct path
        const path = [];
        let curr = current;
        let cKey = curKey;

        while (curr) {
          path.unshift({ x: curr.x, y: curr.y });
          curr = cameFrom.get(cKey) || null;
          if (curr) cKey = getKey(curr.x, curr.y);
        }

        // Determine target wall if ground troop hits an intervening wall
        let targetWall = null;
        if (!canFly) {
          for (const pt of path) {
            const w = grid.wallMatrix[pt.y][pt.x];
            if (w) {
              targetWall = w;
              break;
            }
          }
        }

        return { path, targetWall, totalCost: gScore.get(curKey) };
      }

      closedSet.add(curKey);

      // 8 directions
      const dirs = [
        { dx: 1, dy: 0, diag: false },
        { dx: -1, dy: 0, diag: false },
        { dx: 0, dy: 1, diag: false },
        { dx: 0, dy: -1, diag: false },
        { dx: 1, dy: 1, diag: true },
        { dx: -1, dy: 1, diag: true },
        { dx: 1, dy: -1, diag: true },
        { dx: -1, dy: -1, diag: true }
      ];

      for (const d of dirs) {
        const nx = current.x + d.dx;
        const ny = current.y + d.dy;

        if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;

        const neighborKey = getKey(nx, ny);
        if (closedSet.has(neighborKey)) continue;

        const stepCost = getTileCost(nx, ny, d.diag);
        if (stepCost >= 9000) continue; // Impassable

        const tentativeG = gScore.get(curKey) + stepCost;

        if (!gScore.has(neighborKey) || tentativeG < gScore.get(neighborKey)) {
          cameFrom.set(neighborKey, { x: current.x, y: current.y });
          gScore.set(neighborKey, tentativeG);
          const f = tentativeG + heuristic(nx, ny);
          fScore.set(neighborKey, f);

          const existing = openSet.find(n => n.x === nx && n.y === ny);
          if (!existing) {
            openSet.push({ x: nx, y: ny, f });
          } else {
            existing.f = f;
          }
        }
      }
    }

    // Direct fallback if no path found
    return {
      path: [{ x: sx, y: sy }, { x: tx, y: ty }],
      targetWall: grid.wallMatrix[ty]?.[tx] || null,
      totalCost: heuristic(sx, sy)
    };
  }
}

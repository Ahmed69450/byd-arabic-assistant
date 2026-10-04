import { TROOP_DEFS } from '../data/troops.js';
import { AStarFinder } from './pathfinding.js';

let _troopNextId = 1;

export class TroopInstance {
  constructor(type, pos, level = 1) {
    this.id = _troopNextId++;
    this.type = type;
    const def = TROOP_DEFS[type] || {
      name: type,
      speed: 2.0,
      range: 1.0,
      attackSpeed: 1.0,
      targetPreference: 'any',
      targetType: 'ground',
      isFlying: false,
      isHero: false,
      levels: [{ hp: 500, dps: 50 }]
    };

    this.name = def.name;
    this.x = pos.x;
    this.y = pos.y;
    this.level = Math.max(1, Math.min(level, def.levels.length));

    const lvlData = def.levels[this.level - 1] || def.levels[0];
    this.currentHp = lvlData.hp;
    this.maxHp = lvlData.hp;
    this.dps = lvlData.dps;
    this.speed = def.speed;
    this.range = def.range;
    this.attackSpeed = def.attackSpeed || 1.0;
    this.housingSpace = def.housingSpace || 1;
    this.targetPreference = def.targetPreference;
    this.targetType = def.targetType;
    this.isFlying = def.isFlying || false;
    this.isHero = def.isHero || false;
    this.color = def.color || '#ff4757';

    this.state = 'IDLE'; // IDLE, MOVING, ATTACKING, DEAD
    this.target = null;
    this.targetWall = null;
    this.path = [];
    this.attackCooldown = 0;
  }

  isDead() {
    return this.currentHp <= 0;
  }

  takeDamage(amount) {
    this.currentHp -= amount;
    if (this.currentHp <= 0) {
      this.currentHp = 0;
      this.state = 'DEAD';
    }
  }
}

export class HeroInstance extends TroopInstance {
  constructor(type, pos, level = 1) {
    super(type, pos, level);
    const def = TROOP_DEFS[type] || {};
    this.isHero = true;
    this.ability = def.ability ? { ...def.ability } : null;
    this.isAbilityActive = false;
    this.abilityUsed = false;
    this.abilityDuration = 5.0; // 5 seconds
    this.abilityTimer = 0;
    this.isStealthed = false;
  }

  activateAbility() {
    if (this.abilityUsed || !this.ability) return;
    this.abilityUsed = true;
    this.isAbilityActive = true;
    this.abilityTimer = this.abilityDuration;

    if (this.ability.recoveryHp) {
      this.currentHp = Math.min(this.maxHp, this.currentHp + this.ability.recoveryHp);
    }
    if (this.type === 'barbarian_king') {
      this.dps *= (this.ability.statBoost || 1.5);
      this.speed *= 1.3;
    }
    if (this.type === 'archer_queen') {
      this.isStealthed = true;
      this.dps *= (this.ability.damageBoost || 2.0);
    }
  }

  updateAbility(deltaSeconds) {
    if (!this.isAbilityActive) return;
    this.abilityTimer -= deltaSeconds;
    if (this.abilityTimer <= 0) {
      this.isAbilityActive = false;
      this.isStealthed = false;
      // Revert boosts
      const def = TROOP_DEFS[this.type];
      const lvlData = def.levels[this.level - 1] || def.levels[0];
      this.dps = lvlData.dps;
      this.speed = def.speed;
    }
  }
}

export class TroopAI {
  static getCandidates(troop, grid, maxCandidates = 3) {
    let eligible = [];

    if (troop.targetPreference === 'defenses') {
      eligible = grid.buildings.filter(b => b.category === 'defense' || b.type === 'town_hall');
      // If no defenses remain, attack any building
      if (eligible.length === 0) {
        eligible = grid.buildings;
      }
    } else if (troop.targetPreference === 'resources') {
      eligible = grid.buildings.filter(b => b.category === 'resource');
      if (eligible.length === 0) {
        eligible = grid.buildings;
      }
    } else {
      eligible = grid.buildings;
    }

    if (eligible.length === 0) return [];

    // Sort by straight Euclidean distance to building center
    const sorted = eligible.slice().sort((a, b) => {
      const distA = Math.hypot(troop.x - (a.x + a.size / 2), troop.y - (a.y + a.size / 2));
      const distB = Math.hypot(troop.x - (b.x + b.size / 2), troop.y - (b.y + b.size / 2));
      return distA - distB;
    });

    return sorted.slice(0, maxCandidates);
  }

  static findTarget(troop, grid) {
    if (grid.buildings.length === 0) {
      troop.target = null;
      troop.targetWall = null;
      troop.path = [];
      return null;
    }

    // Special behavior for Wall Breakers
    if (troop.targetPreference === 'walls') {
      const candidates = this.getCandidates(troop, grid, 3);
      if (candidates.length === 0) return null;

      // Find path to closest candidate and see if it is blocked by a wall
      for (const cand of candidates) {
        const pathResult = AStarFinder.findPath(
          { x: troop.x, y: troop.y },
          { x: cand.x + cand.size / 2, y: cand.y + cand.size / 2 },
          grid,
          { canFly: false, troopDps: troop.dps, isWallBreaker: true, targetBuilding: cand }
        );

        if (pathResult.targetWall) {
          troop.target = {
            ...pathResult.targetWall,
            isWall: true,
            size: 1,
            hp: pathResult.targetWall.hp,
            maxHp: pathResult.targetWall.maxHp
          };
          troop.targetWall = pathResult.targetWall;
          troop.path = pathResult.path;
          return troop.target;
        }
      }

      // If no wall found, target the closest candidate directly
      troop.target = candidates[0];
      troop.targetWall = null;
      return troop.target;
    }

    // Rule of 3 for regular troops
    const candidates = this.getCandidates(troop, grid, 3);
    if (candidates.length === 0) return null;

    let bestCandidate = candidates[0];
    let bestResult = null;
    let minCost = Infinity;

    for (const cand of candidates) {
      const pathResult = AStarFinder.findPath(
        { x: troop.x, y: troop.y },
        { x: cand.x + cand.size / 2, y: cand.y + cand.size / 2 },
        grid,
        {
          canFly: troop.isFlying,
          troopDps: troop.dps,
          isWallBreaker: false,
          targetBuilding: cand
        }
      );

      if (pathResult.totalCost < minCost) {
        minCost = pathResult.totalCost;
        bestCandidate = cand;
        bestResult = pathResult;
      }
    }

    troop.target = bestCandidate;
    troop.targetWall = bestResult ? bestResult.targetWall : null;
    troop.path = bestResult ? bestResult.path : [];

    return bestCandidate;
  }

  static getDistanceToEntity(troop, entity) {
    const minX = entity.x;
    const maxX = entity.x + (entity.size || 1);
    const minY = entity.y;
    const maxY = entity.y + (entity.size || 1);

    const clampedX = Math.max(minX, Math.min(maxX, troop.x));
    const clampedY = Math.max(minY, Math.min(maxY, troop.y));

    return Math.hypot(troop.x - clampedX, troop.y - clampedY);
  }

  static updateTroop(troop, deltaSeconds, grid) {
    if (troop.isDead()) return;

    if (troop.isHero && troop.updateAbility) {
      troop.updateAbility(deltaSeconds);
    }

    // Check if target is destroyed or non-existent
    if (troop.target) {
      if (troop.target.isWall) {
        const currentWall = grid.getWallAt(troop.target.x, troop.target.y);
        if (!currentWall || currentWall.hp <= 0) {
          troop.target = null;
          troop.targetWall = null;
        }
      } else {
        const currentBuilding = grid.buildings.find(b => b.instanceId === troop.target.instanceId);
        if (!currentBuilding || currentBuilding.hp <= 0) {
          troop.target = null;
          troop.targetWall = null;
        }
      }
    }

    // If no target, locate target via Rule of 3
    if (!troop.target) {
      this.findTarget(troop, grid);
      if (!troop.target) {
        troop.state = 'IDLE';
        return;
      }
    }

    // If troop is blocked by a wall, prioritize attacking the blocking wall first
    let activeTarget = troop.target;
    if (troop.targetWall && !troop.isFlying) {
      const wallTile = grid.getWallAt(troop.targetWall.x, troop.targetWall.y);
      if (wallTile && wallTile.hp > 0) {
        activeTarget = {
          ...wallTile,
          isWall: true,
          size: 1
        };
      } else {
        troop.targetWall = null;
      }
    }

    const dist = this.getDistanceToEntity(troop, activeTarget);

    if (dist <= troop.range + 0.1) {
      // In attack range
      troop.state = 'ATTACKING';
      troop.attackCooldown -= deltaSeconds;

      if (troop.attackCooldown <= 0) {
        troop.attackCooldown = troop.attackSpeed;

        const isWallBreakerExplode = troop.type === 'wall_breaker';
        const damageMultiplier = (isWallBreakerExplode && activeTarget.isWall) ? 40 : 1;
        const damageDealt = troop.dps * troop.attackSpeed * damageMultiplier;

        if (activeTarget.isWall) {
          const w = grid.getWallAt(activeTarget.x, activeTarget.y);
          if (w) {
            w.hp -= damageDealt;
            if (w.hp <= 0) {
              grid.removeWall(w.x, w.y);
              troop.targetWall = null;
              this.findTarget(troop, grid);
            }
          }
        } else {
          const b = grid.buildings.find(item => item.instanceId === activeTarget.instanceId);
          if (b) {
            b.hp -= damageDealt;
            if (b.hp <= 0) {
              grid.removeBuildingAt(b.x, b.y);
              troop.target = null;
              this.findTarget(troop, grid);
            }
          }
        }

        if (isWallBreakerExplode) {
          troop.takeDamage(999999); // Wall breaker dies upon bomb detonation
        }
      }
    } else {
      // Move towards target
      troop.state = 'MOVING';

      let nextWaypoint = null;
      if (troop.path && troop.path.length > 0) {
        // Find waypoint ahead of troop
        while (troop.path.length > 0) {
          const wp = troop.path[0];
          const wpDist = Math.hypot(troop.x - (wp.x + 0.5), troop.y - (wp.y + 0.5));
          if (wpDist < 0.3) {
            troop.path.shift(); // Reached waypoint
          } else {
            nextWaypoint = { x: wp.x + 0.5, y: wp.y + 0.5 };
            break;
          }
        }
      }

      if (!nextWaypoint) {
        // Direct move to target center
        nextWaypoint = {
          x: activeTarget.x + (activeTarget.size || 1) / 2,
          y: activeTarget.y + (activeTarget.size || 1) / 2
        };
      }

      const moveDist = troop.speed * deltaSeconds;
      const angle = Math.atan2(nextWaypoint.y - troop.y, nextWaypoint.x - troop.x);
      troop.x += Math.cos(angle) * moveDist;
      troop.y += Math.sin(angle) * moveDist;
    }
  }
}

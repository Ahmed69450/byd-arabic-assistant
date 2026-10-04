import { VillageGrid } from '../engine/grid.js';
import { TroopInstance, HeroInstance, TroopAI } from './ai.js';
import { Projectile, ParticleEffect } from './fx.js';
import { TROOP_DEFS } from '../data/troops.js';

export class CombatSimulator {
  constructor(sourceGrid, armyList = []) {
    // Clone grid so simulation runs independently without mutating the original village
    this.grid = new VillageGrid(sourceGrid.size);
    this.grid.importLayout(sourceGrid.exportLayout());

    this.troops = [];
    this.projectiles = [];
    this.effects = [];
    this.defenseCooldowns = new Map();

    this.totalBuildingsCount = this.grid.buildings.length;
    this.destroyedBuildingsCount = 0;
    this.townHallDestroyed = false;
    this.destructionPercent = 0;
    this.stars = 0;

    this.currentTime = 0;
    this.maxTime = 180; // 3 minutes
    this.isOver = false;

    // Deploy initial army if provided
    for (const item of armyList) {
      const count = item.count || 1;
      const lvl = item.level || 10;
      for (let i = 0; i < count; i++) {
        // Slight jitter in coordinates to avoid stacking exactly on one spot
        const jx = (item.x || 2) + (Math.random() - 0.5) * 1.5;
        const jy = (item.y || 2) + (Math.random() - 0.5) * 1.5;
        this.deployTroop(item.type, jx, jy, lvl, true);
      }
    }
  }

  deployTroop(type, x, y, level = 10, bypassBufferCheck = false) {
    if (this.isOver) return null;

    if (!bypassBufferCheck && !this.grid.isDropZoneValid(x, y)) {
      return null;
    }

    const def = TROOP_DEFS[type];
    let troop = null;
    if (def && def.isHero) {
      troop = new HeroInstance(type, { x, y }, level);
    } else {
      troop = new TroopInstance(type, { x, y }, level);
    }

    this.troops.push(troop);
    return troop;
  }

  step(deltaTime = 0.1) {
    if (this.isOver) return this.getState();

    this.currentTime += deltaTime;

    // 1. Defenses targeting and firing
    for (const b of this.grid.buildings) {
      if (b.category !== 'defense' && b.type !== 'town_hall') continue;
      if (b.range <= 0) continue;

      const cd = this.defenseCooldowns.get(b.instanceId) || 0;
      if (cd > 0) {
        this.defenseCooldowns.set(b.instanceId, cd - deltaTime);
        continue;
      }

      const bx = b.x + b.size / 2;
      const by = b.y + b.size / 2;

      // Find closest alive eligible troop
      let targetTroop = null;
      let minDistance = Infinity;

      for (const t of this.troops) {
        if (t.isDead()) continue;
        if (t.isStealthed) continue; // Cannot target stealthed troops (Queen's ability)

        // Check target type (ground / air)
        if (b.targetType === 'ground' && t.isFlying) continue;
        if (b.targetType === 'air' && !t.isFlying) continue;

        const dist = Math.hypot(bx - t.x, by - t.y);
        if (dist <= b.range && dist >= (b.minRange || 0)) {
          if (dist < minDistance) {
            minDistance = dist;
            targetTroop = t;
          }
        }
      }

      if (targetTroop) {
        // Fire at target
        const attackInterval = b.type === 'mortar' ? 5.0 : (b.type === 'x_bow' ? 0.128 : 1.0);
        this.defenseCooldowns.set(b.instanceId, attackInterval);

        const damagePerShot = b.dps * attackInterval;
        const isSplash = b.damageType === 'splash' || b.type === 'mortar' || b.type === 'wizard_tower';
        const splashR = b.type === 'mortar' ? 1.5 : (b.type === 'wizard_tower' ? 1.0 : 0);

        this.projectiles.push(new Projectile({
          type: b.type === 'mortar' ? 'mortar' : (b.type === 'air_defense' ? 'rocket' : 'cannonball'),
          startX: bx,
          startY: by,
          targetX: targetTroop.x,
          targetY: targetTroop.y,
          targetTroop,
          speed: b.type === 'mortar' ? 5.0 : 12.0,
          damage: damagePerShot,
          splashRadius: splashR,
          isArc: b.type === 'mortar'
        }));
      }
    }

    // 2. Update Projectiles
    for (let i = this.projectiles.length - 1; i >= 0; i--) {
      const p = this.projectiles[i];
      p.update(deltaTime);

      if (p.completed) {
        // Apply damage on impact
        if (p.splashRadius > 0) {
          // Splash damage to all nearby troops
          for (const t of this.troops) {
            if (t.isDead()) continue;
            const dist = Math.hypot(t.x - p.x, t.y - p.y);
            if (dist <= p.splashRadius) {
              t.takeDamage(p.damage);
            }
          }
        } else if (p.targetTroop && !p.targetTroop.isDead()) {
          p.targetTroop.takeDamage(p.damage);
        }

        // Add impact VFX
        this.effects.push(new ParticleEffect({
          x: p.x,
          y: p.y,
          type: 'explosion',
          color: '#ff9f43',
          radius: p.splashRadius > 0 ? 1.5 : 0.8
        }));

        this.projectiles.splice(i, 1);
      }
    }

    // 3. Update Troops AI and combat
    for (const t of this.troops) {
      if (!t.isDead()) {
        TroopAI.updateTroop(t, deltaTime, this.grid);
      }
    }

    // 4. Update Effects
    for (let i = this.effects.length - 1; i >= 0; i--) {
      const ef = this.effects[i];
      ef.update(deltaTime);
      if (ef.isDead()) {
        this.effects.splice(i, 1);
      }
    }

    // 5. Update Destruction Percentage and Stars
    this.destroyedBuildingsCount = this.totalBuildingsCount - this.grid.buildings.length;
    this.destructionPercent = this.totalBuildingsCount > 0
      ? Math.round((this.destroyedBuildingsCount / this.totalBuildingsCount) * 100)
      : 100;

    const hasTownHall = this.grid.buildings.some(b => b.type === 'town_hall');
    if (!hasTownHall && this.totalBuildingsCount > 0) {
      this.townHallDestroyed = true;
    }

    let earnedStars = 0;
    if (this.townHallDestroyed) earnedStars++;
    if (this.destructionPercent >= 50) earnedStars++;
    if (this.destructionPercent >= 100) earnedStars = 3;
    this.stars = earnedStars;

    // Check Termination Conditions
    const aliveTroopsCount = this.troops.filter(t => !t.isDead()).length;
    if (this.destructionPercent >= 100) {
      this.isOver = true;
    } else if (this.currentTime >= this.maxTime) {
      this.isOver = true;
    } else if (this.troops.length > 0 && aliveTroopsCount === 0 && this.projectiles.length === 0) {
      this.isOver = true;
    }

    return this.getState();
  }

  runToCompletion(maxDuration = 180) {
    const dt = 0.1;
    while (!this.isOver && this.currentTime < maxDuration) {
      this.step(dt);
    }

    return {
      stars: this.stars,
      destructionPercent: this.destructionPercent,
      duration: Math.round(this.currentTime * 10) / 10,
      isVictory: this.stars >= 1,
      totalBuildings: this.totalBuildingsCount,
      destroyedCount: this.destroyedBuildingsCount
    };
  }

  getState() {
    return {
      currentTime: Math.round(this.currentTime * 10) / 10,
      maxTime: this.maxTime,
      destructionPercent: this.destructionPercent,
      stars: this.stars,
      isOver: this.isOver,
      troops: this.troops,
      projectiles: this.projectiles,
      effects: this.effects,
      remainingBuildings: this.grid.buildings.length
    };
  }
}

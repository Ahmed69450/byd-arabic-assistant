let _fxNextId = 1;

export class Projectile {
  constructor(options) {
    this.id = _fxNextId++;
    this.type = options.type || 'arrow'; // arrow, cannonball, mortar, rocket, beam
    this.startX = options.startX;
    this.startY = options.startY;
    this.x = options.startX;
    this.y = options.startY;
    this.targetX = options.targetX;
    this.targetY = options.targetY;
    this.targetTroop = options.targetTroop || null;
    this.speed = options.speed || 8.0; // tiles per second
    this.damage = options.damage || 50;
    this.splashRadius = options.splashRadius || 0;
    this.isArc = options.isArc || false;
    this.progress = 0; // 0.0 to 1.0
    this.distance = Math.hypot(this.targetX - this.startX, this.targetY - this.startY) || 0.1;
    this.duration = this.distance / this.speed;
    this.elapsed = 0;
    this.completed = false;
  }

  update(deltaTime) {
    this.elapsed += deltaTime;
    this.progress = Math.min(1.0, this.elapsed / this.duration);

    if (this.targetTroop && !this.targetTroop.isDead() && this.type !== 'mortar') {
      this.targetX = this.targetTroop.x;
      this.targetY = this.targetTroop.y;
    }

    this.x = this.startX + (this.targetX - this.startX) * this.progress;
    this.y = this.startY + (this.targetY - this.startY) * this.progress;

    if (this.progress >= 1.0) {
      this.completed = true;
    }
  }
}

export class ParticleEffect {
  constructor(options) {
    this.id = _fxNextId++;
    this.x = options.x;
    this.y = options.y;
    this.type = options.type || 'explosion'; // explosion, damageText, spark
    this.text = options.text || '';
    this.color = options.color || '#ff9f43';
    this.radius = options.radius || 1.0;
    this.maxLife = options.maxLife || 0.5;
    this.life = this.maxLife;
  }

  update(deltaTime) {
    this.life -= deltaTime;
    if (this.type === 'damageText') {
      this.y -= deltaTime * 1.5; // Float upwards
    }
  }

  isDead() {
    return this.life <= 0;
  }
}

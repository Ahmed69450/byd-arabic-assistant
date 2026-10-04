export class IsometricRenderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');

    this.tileWidth = 44;
    this.tileHeight = 22;
    this.zoom = 1.0;
    this.originX = canvas.width / 2;
    this.originY = 120;

    this.selectedBuilding = null;
    this.showGridLines = true;
    this.showSpawnerBuffer = true;
    this.showHeatmap = false;
    this.heatmapType = 'ground';
  }

  resize(width, height) {
    this.canvas.width = width;
    this.canvas.height = height;
    this.originX = width / 2;
    this.originY = Math.max(80, height * 0.12);
  }

  gridToScreen(gx, gy) {
    const halfW = (this.tileWidth / 2) * this.zoom;
    const halfH = (this.tileHeight / 2) * this.zoom;
    return {
      x: (gx - gy) * halfW + this.originX,
      y: (gx + gy) * halfH + this.originY
    };
  }

  screenToGrid(sx, sy) {
    const halfW = (this.tileWidth / 2) * this.zoom;
    const halfH = (this.tileHeight / 2) * this.zoom;
    const dx = sx - this.originX;
    const dy = sy - this.originY;

    const gx = (dx / halfW + dy / halfH) / 2;
    const gy = (dy / halfH - dx / halfW) / 2;
    return {
      x: Math.floor(gx),
      y: Math.floor(gy)
    };
  }

  render(grid, combatSim = null, viewOptions = {}) {
    const ctx = this.ctx;
    const w = this.canvas.width;
    const h = this.canvas.height;

    ctx.clearRect(0, 0, w, h);

    // 1. Draw Field Background
    this.drawTerrain(grid);

    // 2. Draw Spawner Exclusion Buffer (Red Border)
    if (this.showSpawnerBuffer) {
      this.drawSpawnerBuffer(grid);
    }

    // 3. Draw Heatmap (if enabled)
    if (this.showHeatmap) {
      this.drawHeatmapOverlay(grid);
    }

    // 4. Draw Selected Range Circle (on ground level)
    if (this.selectedBuilding) {
      this.drawRangeCircle(this.selectedBuilding);
    }

    // 5. Draw Walls
    this.drawWalls(grid);

    // 6. Draw Buildings sorted by isometric depth (gx + gy + size)
    this.drawBuildings(grid);

    // 7. Draw Troops (if simulator active)
    if (combatSim) {
      this.drawTroops(combatSim.troops);
      this.drawProjectiles(combatSim.projectiles);
      this.drawEffects(combatSim.effects);
    }
  }

  drawTerrain(grid) {
    const ctx = this.ctx;
    const size = grid.size;

    // Grass fill
    ctx.save();
    ctx.beginPath();
    const pTop = this.gridToScreen(0, 0);
    const pRight = this.gridToScreen(size, 0);
    const pBottom = this.gridToScreen(size, size);
    const pLeft = this.gridToScreen(0, size);

    ctx.moveTo(pTop.x, pTop.y);
    ctx.lineTo(pRight.x, pRight.y);
    ctx.lineTo(pBottom.x, pBottom.y);
    ctx.lineTo(pLeft.x, pLeft.y);
    ctx.closePath();

    const grad = ctx.createLinearGradient(pTop.x, pTop.y, pBottom.x, pBottom.y);
    grad.addColorStop(0, '#4b8b3b');
    grad.addColorStop(0.5, '#427e34');
    grad.addColorStop(1, '#3a702d');
    ctx.fillStyle = grad;
    ctx.fill();

    // Wood/Stone Border
    ctx.lineWidth = 3 * this.zoom;
    ctx.strokeStyle = '#2b4f21';
    ctx.stroke();

    // Subtle Grid Lines
    if (this.showGridLines && this.zoom >= 0.7) {
      ctx.lineWidth = 0.5 * this.zoom;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';

      for (let i = 0; i <= size; i++) {
        const startH = this.gridToScreen(0, i);
        const endH = this.gridToScreen(size, i);
        ctx.beginPath();
        ctx.moveTo(startH.x, startH.y);
        ctx.lineTo(endH.x, endH.y);
        ctx.stroke();

        const startV = this.gridToScreen(i, 0);
        const endV = this.gridToScreen(i, size);
        ctx.beginPath();
        ctx.moveTo(startV.x, startV.y);
        ctx.lineTo(endV.x, endV.y);
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  drawSpawnerBuffer(grid) {
    const ctx = this.ctx;
    ctx.save();
    ctx.fillStyle = 'rgba(235, 77, 75, 0.15)';
    ctx.strokeStyle = 'rgba(235, 77, 75, 0.4)';
    ctx.lineWidth = 1 * this.zoom;

    for (let y = 0; y < grid.size; y++) {
      for (let x = 0; x < grid.size; x++) {
        if (grid.spawnerBuffer[y][x] && grid.buildingMatrix[y][x] === null && grid.wallMatrix[y][x] === null) {
          const pt = this.gridToScreen(x, y);
          const pr = this.gridToScreen(x + 1, y);
          const pb = this.gridToScreen(x + 1, y + 1);
          const pl = this.gridToScreen(x, y + 1);

          ctx.beginPath();
          ctx.moveTo(pt.x, pt.y);
          ctx.lineTo(pr.x, pr.y);
          ctx.lineTo(pb.x, pb.y);
          ctx.lineTo(pl.x, pl.y);
          ctx.closePath();
          ctx.fill();
        }
      }
    }
    ctx.restore();
  }

  drawHeatmapOverlay(grid) {
    const ctx = this.ctx;
    const heatmap = grid.getCoverageHeatmap(this.heatmapType);
    let maxVal = 1;
    for (let y = 0; y < grid.size; y++) {
      for (let x = 0; x < grid.size; x++) {
        if (heatmap[y][x] > maxVal) maxVal = heatmap[y][x];
      }
    }

    ctx.save();
    for (let y = 0; y < grid.size; y++) {
      for (let x = 0; x < grid.size; x++) {
        const val = heatmap[y][x];
        if (val > 0) {
          const intensity = Math.min(1.0, val / maxVal);
          const pt = this.gridToScreen(x, y);
          const pr = this.gridToScreen(x + 1, y);
          const pb = this.gridToScreen(x + 1, y + 1);
          const pl = this.gridToScreen(x, y + 1);

          ctx.fillStyle = this.heatmapType === 'air'
            ? `rgba(74, 144, 226, ${intensity * 0.5})`
            : `rgba(235, 94, 40, ${intensity * 0.5})`;

          ctx.beginPath();
          ctx.moveTo(pt.x, pt.y);
          ctx.lineTo(pr.x, pr.y);
          ctx.lineTo(pb.x, pb.y);
          ctx.lineTo(pl.x, pl.y);
          ctx.closePath();
          ctx.fill();
        }
      }
    }
    ctx.restore();
  }

  drawRangeCircle(building) {
    if (!building || !building.range || building.range <= 0) return;
    const ctx = this.ctx;
    const cx = building.x + building.size / 2;
    const cy = building.y + building.size / 2;
    const centerScreen = this.gridToScreen(cx, cy);

    const radiusX = building.range * (this.tileWidth / 2) * this.zoom;
    const radiusY = building.range * (this.tileHeight / 2) * this.zoom;

    ctx.save();
    ctx.beginPath();
    ctx.ellipse(centerScreen.x, centerScreen.y, radiusX, radiusY, 0, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.fill();
    ctx.lineWidth = 2 * this.zoom;
    ctx.strokeStyle = '#f1c40f';
    ctx.setLineDash([6, 4]);
    ctx.stroke();

    // Min Range circle if applicable (e.g. Mortar / Eagle)
    if (building.minRange && building.minRange > 0) {
      const minX = building.minRange * (this.tileWidth / 2) * this.zoom;
      const minY = building.minRange * (this.tileHeight / 2) * this.zoom;
      ctx.beginPath();
      ctx.ellipse(centerScreen.x, centerScreen.y, minX, minY, 0, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(231, 76, 60, 0.2)';
      ctx.fill();
      ctx.strokeStyle = '#e74c3c';
      ctx.stroke();
    }
    ctx.restore();
  }

  drawWalls(grid) {
    const ctx = this.ctx;
    const halfW = (this.tileWidth / 2) * this.zoom;
    const halfH = (this.tileHeight / 2) * this.zoom;
    const wallHeight = 14 * this.zoom;

    ctx.save();
    for (const w of grid.walls) {
      const pos = this.gridToScreen(w.x, w.y);
      const adj = grid.getWallAdjacency(w.x, w.y);

      // Stylized high-level Clash Wall
      // Base diamond
      const topX = pos.x;
      const topY = pos.y - wallHeight;

      // Color scheme based on level
      const isLava = (w.level || 11) >= 11;
      const wallColor = isLava ? '#1e272e' : '#485460';
      const wallAccent = isLava ? '#ff4757' : '#ffd32a';

      // Left column face
      ctx.fillStyle = wallColor;
      ctx.beginPath();
      ctx.moveTo(pos.x - halfW, pos.y);
      ctx.lineTo(pos.x, pos.y + halfH);
      ctx.lineTo(pos.x, pos.y + halfH - wallHeight);
      ctx.lineTo(pos.x - halfW, pos.y - wallHeight);
      ctx.closePath();
      ctx.fill();

      // Right column face
      ctx.fillStyle = '#2f3542';
      ctx.beginPath();
      ctx.moveTo(pos.x, pos.y + halfH);
      ctx.lineTo(pos.x + halfW, pos.y);
      ctx.lineTo(pos.x + halfW, pos.y - wallHeight);
      ctx.lineTo(pos.x, pos.y + halfH - wallHeight);
      ctx.closePath();
      ctx.fill();

      // Top face
      ctx.fillStyle = isLava ? '#2f3640' : '#718093';
      ctx.beginPath();
      ctx.moveTo(topX, topY - halfH);
      ctx.lineTo(topX + halfW, topY);
      ctx.lineTo(topX, topY + halfH);
      ctx.lineTo(topX - halfW, topY);
      ctx.closePath();
      ctx.fill();

      // Glowing core line
      ctx.strokeStyle = wallAccent;
      ctx.lineWidth = 2 * this.zoom;
      ctx.beginPath();
      ctx.moveTo(topX - halfW * 0.4, topY);
      ctx.lineTo(topX + halfW * 0.4, topY);
      ctx.stroke();

      // Connectors
      if (adj.south) {
        ctx.fillStyle = wallAccent;
        ctx.fillRect(pos.x - 2 * this.zoom, pos.y + halfH * 0.5 - wallHeight, 4 * this.zoom, 4 * this.zoom);
      }
    }
    ctx.restore();
  }

  drawBuildings(grid) {
    const ctx = this.ctx;

    // Sort buildings for isometric depth sorting
    const sorted = grid.buildings.slice().sort((a, b) => {
      const depthA = a.x + a.y + a.size;
      const depthB = b.x + b.y + b.size;
      return depthA - depthB;
    });

    for (const b of sorted) {
      this.drawBuildingEntity(b, b === this.selectedBuilding);
    }
  }

  drawBuildingEntity(b, isSelected) {
    const ctx = this.ctx;
    const halfW = (this.tileWidth / 2) * this.zoom;
    const halfH = (this.tileHeight / 2) * this.zoom;
    const bSize = b.size;

    const pTop = this.gridToScreen(b.x, b.y);
    const pRight = this.gridToScreen(b.x + bSize, b.y);
    const pBottom = this.gridToScreen(b.x + bSize, b.y + bSize);
    const pLeft = this.gridToScreen(b.x, b.y + bSize);

    const centerX = (pLeft.x + pRight.x) / 2;
    const centerY = (pTop.y + pBottom.y) / 2;

    const bHeight = (bSize * 10 + (b.type === 'town_hall' ? 24 : 14)) * this.zoom;

    ctx.save();

    // 1. Shadow
    ctx.beginPath();
    ctx.ellipse(centerX, pBottom.y, (bSize * halfW) * 0.75, (bSize * halfH) * 0.4, 0, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
    ctx.fill();

    // 2. Base Foundation Tile
    ctx.beginPath();
    ctx.moveTo(pTop.x, pTop.y);
    ctx.lineTo(pRight.x, pRight.y);
    ctx.lineTo(pBottom.x, pBottom.y);
    ctx.lineTo(pLeft.x, pLeft.y);
    ctx.closePath();
    ctx.fillStyle = '#2c3e50';
    ctx.fill();
    ctx.lineWidth = 1 * this.zoom;
    ctx.strokeStyle = '#1a252f';
    ctx.stroke();

    // 3. 3D Walls (Left & Right Faces)
    ctx.fillStyle = '#34495e';
    ctx.beginPath();
    ctx.moveTo(pLeft.x, pLeft.y);
    ctx.lineTo(pBottom.x, pBottom.y);
    ctx.lineTo(pBottom.x, pBottom.y - bHeight);
    ctx.lineTo(pLeft.x, pLeft.y - bHeight);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = '#2c3e50';
    ctx.beginPath();
    ctx.moveTo(pBottom.x, pBottom.y);
    ctx.lineTo(pRight.x, pRight.y);
    ctx.lineTo(pRight.x, pRight.y - bHeight);
    ctx.lineTo(pBottom.x, pBottom.y - bHeight);
    ctx.closePath();
    ctx.fill();

    // 4. Top Roof Face
    ctx.fillStyle = b.color || '#3498db';
    ctx.beginPath();
    ctx.moveTo(pTop.x, pTop.y - bHeight);
    ctx.lineTo(pRight.x, pRight.y - bHeight);
    ctx.lineTo(pBottom.x, pBottom.y - bHeight);
    ctx.lineTo(pLeft.x, pLeft.y - bHeight);
    ctx.closePath();
    ctx.fill();
    ctx.lineWidth = 1.5 * this.zoom;
    ctx.strokeStyle = '#ffffff33';
    ctx.stroke();

    // 5. Stylized Details based on building type
    this.drawBuildingDetails(b, centerX, centerY - bHeight);

    // 6. Selection Glow
    if (isSelected) {
      ctx.strokeStyle = '#f1c40f';
      ctx.lineWidth = 3 * this.zoom;
      ctx.stroke();
    }

    // 7. Health Bar if Damaged
    if (b.hp < b.maxHp && b.hp > 0) {
      const barW = Math.max(28 * this.zoom, bSize * 10 * this.zoom);
      const barH = 5 * this.zoom;
      const barX = centerX - barW / 2;
      const barY = centerY - bHeight - 12 * this.zoom;

      ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
      ctx.fillRect(barX - 1, barY - 1, barW + 2, barH + 2);

      const healthRatio = Math.max(0, b.hp / b.maxHp);
      ctx.fillStyle = healthRatio > 0.5 ? '#2ecc71' : healthRatio > 0.2 ? '#f39c12' : '#e74c3c';
      ctx.fillRect(barX, barY, barW * healthRatio, barH);
    }

    ctx.restore();
  }

  drawBuildingDetails(b, cx, cy) {
    const ctx = this.ctx;
    const z = this.zoom;

    ctx.save();
    if (b.type === 'town_hall') {
      // Golden Crown Crest / TH Chimney
      ctx.fillStyle = '#f39c12';
      ctx.beginPath();
      ctx.arc(cx, cy, 7 * z, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#d35400';
      ctx.fillRect(cx - 3 * z, cy - 3 * z, 6 * z, 6 * z);

      // Level badge
      ctx.fillStyle = '#ffffff';
      ctx.font = `bold ${Math.round(9 * z)}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(`TH${b.level}`, cx, cy + 12 * z);
    } else if (b.type === 'cannon') {
      // Cannon Barrel
      ctx.fillStyle = '#1e272e';
      ctx.beginPath();
      ctx.arc(cx, cy, 6 * z, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#485460';
      ctx.fillRect(cx - 3 * z, cy - 10 * z, 6 * z, 8 * z);
    } else if (b.type === 'archer_tower') {
      // Elevated Watchtower Platform
      ctx.fillStyle = '#d2b48c';
      ctx.fillRect(cx - 6 * z, cy - 6 * z, 12 * z, 12 * z);
      ctx.fillStyle = '#27ae60';
      ctx.beginPath();
      ctx.arc(cx, cy, 3 * z, 0, Math.PI * 2);
      ctx.fill();
    } else if (b.type === 'air_defense') {
      // 4 Angled Red Rockets
      ctx.fillStyle = '#e74c3c';
      ctx.fillRect(cx - 5 * z, cy - 5 * z, 4 * z, 4 * z);
      ctx.fillRect(cx + 1 * z, cy - 5 * z, 4 * z, 4 * z);
      ctx.fillRect(cx - 5 * z, cy + 1 * z, 4 * z, 4 * z);
      ctx.fillRect(cx + 1 * z, cy + 1 * z, 4 * z, 4 * z);
    } else if (b.type === 'inferno_tower') {
      // Magma Orb
      ctx.fillStyle = '#e67e22';
      ctx.beginPath();
      ctx.arc(cx, cy, 7 * z, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#f1c40f';
      ctx.beginPath();
      ctx.arc(cx, cy, 3.5 * z, 0, Math.PI * 2);
      ctx.fill();
    } else if (b.type === 'eagle_artillery') {
      // Eagle Wings
      ctx.strokeStyle = '#f1c40f';
      ctx.lineWidth = 2.5 * z;
      ctx.beginPath();
      ctx.moveTo(cx - 8 * z, cy + 4 * z);
      ctx.lineTo(cx, cy - 6 * z);
      ctx.lineTo(cx + 8 * z, cy + 4 * z);
      ctx.stroke();
    }
    ctx.restore();
  }

  drawTroops(troops) {
    const ctx = this.ctx;
    const z = this.zoom;

    for (const t of troops) {
      if (t.isDead()) continue;
      const screenPos = this.gridToScreen(t.x, t.y);

      ctx.save();
      // Drop Shadow
      ctx.beginPath();
      ctx.ellipse(screenPos.x, screenPos.y, 6 * z, 3 * z, 0, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fill();

      // Elevation for flying troops
      const elev = t.isFlying ? 20 * z : 0;
      const drawY = screenPos.y - elev;

      // Hero Aura
      if (t.isHero && t.isAbilityActive) {
        ctx.beginPath();
        ctx.arc(screenPos.x, drawY, 14 * z, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(241, 196, 15, 0.35)';
        ctx.fill();
      }

      // Troop Body Token
      ctx.beginPath();
      const radius = (t.isHero ? 9 : (t.type === 'pekka' || t.type === 'giant') ? 8 : 5) * z;
      ctx.arc(screenPos.x, drawY, radius, 0, Math.PI * 2);
      ctx.fillStyle = t.color;
      ctx.fill();
      ctx.lineWidth = 1.5 * z;
      ctx.strokeStyle = '#ffffff';
      ctx.stroke();

      // Name initial
      ctx.fillStyle = '#ffffff';
      ctx.font = `bold ${Math.round(8 * z)}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(t.name.substring(0, 1), screenPos.x, drawY);

      // Troop HP bar
      const barW = 16 * z;
      const barH = 3 * z;
      const barX = screenPos.x - barW / 2;
      const barY = drawY - radius - 6 * z;

      ctx.fillStyle = 'rgba(0,0,0,0.7)';
      ctx.fillRect(barX, barY, barW, barH);
      const ratio = Math.max(0, t.currentHp / t.maxHp);
      ctx.fillStyle = ratio > 0.5 ? '#2ecc71' : '#e74c3c';
      ctx.fillRect(barX, barY, barW * ratio, barH);

      ctx.restore();
    }
  }

  drawProjectiles(projectiles) {
    const ctx = this.ctx;
    const z = this.zoom;

    for (const p of projectiles) {
      const pos = this.gridToScreen(p.x, p.y);
      let arcHeight = 0;
      if (p.isArc) {
        // Parabolic arc for mortar shells
        arcHeight = Math.sin(p.progress * Math.PI) * 45 * z;
      }

      ctx.save();
      ctx.beginPath();
      ctx.arc(pos.x, pos.y - arcHeight, 3.5 * z, 0, Math.PI * 2);
      ctx.fillStyle = p.type === 'mortar' ? '#2c3e50' : p.type === 'rocket' ? '#e74c3c' : '#f1c40f';
      ctx.fill();
      ctx.restore();
    }
  }

  drawEffects(effects) {
    const ctx = this.ctx;
    const z = this.zoom;

    for (const ef of effects) {
      const pos = this.gridToScreen(ef.x, ef.y);
      const progress = 1 - (ef.life / ef.maxLife);

      ctx.save();
      if (ef.type === 'explosion') {
        const radius = (ef.radius * 20 * (1 + progress)) * z;
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(235, 94, 40, ${1 - progress})`;
        ctx.fill();
      }
      ctx.restore();
    }
  }
}

import { BUILDING_DEFS } from './data/buildings.js';
import { TROOP_DEFS } from './data/troops.js';
import { DEFAULT_LAYOUTS } from './data/default_layouts.js';
import { VillageGrid } from './engine/grid.js';
import { IsometricRenderer } from './engine/renderer.js';
import { LayoutAnalyzer, LayoutGenerator } from './engine/optimizer.js';
import { CombatSimulator } from './simulation/combat.js';

class ClashApp {
  constructor() {
    this.canvas = document.getElementById('villageCanvas');
    this.grid = new VillageGrid(44);
    this.renderer = new IsometricRenderer(this.canvas);
    this.combatSimulator = null;

    this.mode = 'build'; // 'build' | 'battle'
    this.currentThLevel = 11;
    this.selectedTool = 'cannon';
    this.activeDockCategory = 'defense';

    this.isMouseDown = false;
    this.isDragging = false;
    this.dragStart = { x: 0, y: 0 };
    this.lastMouse = { x: 0, y: 0 };

    this.lastTimestamp = performance.now();
  }

  init() {
    this.setupResize();
    this.loadDefaultLayout(11);
    this.setupUIEvents();
    this.renderDockPalette();
    this.updateScoreBadge();

    // Start Animation Loop
    requestAnimationFrame((t) => this.loop(t));
  }

  setupResize() {
    const resize = () => {
      const container = this.canvas.parentElement;
      this.renderer.resize(container.clientWidth, container.clientHeight);
    };
    window.addEventListener('resize', resize);
    resize();
  }

  loadDefaultLayout(thLevel) {
    this.currentThLevel = thLevel;
    const key = `TH${thLevel}`;
    const layout = DEFAULT_LAYOUTS[key] || DEFAULT_LAYOUTS['TH11'];
    this.grid.importLayout(layout);
    this.updateScoreBadge();
  }

  updateScoreBadge() {
    const report = LayoutAnalyzer.evaluateBase(this.grid);
    const circle = document.getElementById('overallScoreCircle');
    const label = document.getElementById('scoreLabel');

    if (circle && label) {
      circle.textContent = report.overallScore;
      if (report.overallScore >= 80) {
        circle.style.background = '#2ecc71';
        label.textContent = 'ممتاز (قاعدة حصينة)';
      } else if (report.overallScore >= 60) {
        circle.style.background = '#f39c12';
        label.textContent = 'جيد (متوازنة)';
      } else {
        circle.style.background = '#e74c3c';
        label.textContent = 'ضعيف (تحتاج تحسين)';
      }
    }
  }

  setupUIEvents() {
    // Canvas Mouse / Gesture Controls
    this.canvas.addEventListener('mousedown', (e) => this.onMouseDown(e));
    window.addEventListener('mousemove', (e) => this.onMouseMove(e));
    window.addEventListener('mouseup', (e) => this.onMouseUp(e));
    this.canvas.addEventListener('wheel', (e) => this.onWheel(e), { passive: false });

    // Town Hall Selector
    const thSelect = document.getElementById('thSelect');
    thSelect.addEventListener('change', (e) => {
      this.loadDefaultLayout(parseInt(e.target.value, 10));
    });

    // Preset Button
    document.getElementById('btnLoadPreset').addEventListener('click', () => {
      const th = parseInt(thSelect.value, 10);
      this.loadDefaultLayout(th);
    });

    // Auto Optimize Button
    document.getElementById('btnOptimize').addEventListener('click', () => {
      LayoutGenerator.autoOptimize(this.grid);
      this.updateScoreBadge();
    });

    // Analysis Report Button
    document.getElementById('btnAnalysisReport').addEventListener('click', () => {
      this.openAnalysisReport();
    });
    document.getElementById('btnCloseAnalysis').addEventListener('click', () => {
      document.getElementById('analysisModal').classList.add('hidden');
    });
    document.getElementById('btnCloseAnalysisFooter').addEventListener('click', () => {
      document.getElementById('analysisModal').classList.add('hidden');
    });

    // Mode Toggle (Build / Battle)
    const btnToggleMode = document.getElementById('btnToggleMode');
    btnToggleMode.addEventListener('click', () => {
      if (this.mode === 'build') {
        this.setMode('battle');
      } else {
        this.setMode('build');
      }
    });

    // Clear Button
    document.getElementById('btnClear').addEventListener('click', () => {
      this.grid.clear();
      this.updateScoreBadge();
    });

    // Zoom Controls
    document.getElementById('btnZoomIn').addEventListener('click', () => {
      this.renderer.zoom = Math.min(2.5, this.renderer.zoom * 1.2);
    });
    document.getElementById('btnZoomOut').addEventListener('click', () => {
      this.renderer.zoom = Math.max(0.4, this.renderer.zoom / 1.2);
    });
    document.getElementById('btnResetView').addEventListener('click', () => {
      this.renderer.zoom = 1.0;
      this.renderer.originX = this.canvas.width / 2;
      this.renderer.originY = 120;
    });

    // Heatmap Selector
    document.getElementById('heatmapSelect').addEventListener('change', (e) => {
      if (e.target.value === 'none') {
        this.renderer.showHeatmap = false;
      } else {
        this.renderer.showHeatmap = true;
        this.renderer.heatmapType = e.target.value;
      }
    });

    // Dock Tabs
    const tabs = document.querySelectorAll('.dock-tab');
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        this.activeDockCategory = tab.dataset.category;
        this.renderDockPalette();
      });
    });

    // Battle Simulator Controls
    document.getElementById('btnRunBenchmark').addEventListener('click', () => {
      this.runAutoBenchmark();
    });
    document.getElementById('btnResetBattle').addEventListener('click', () => {
      this.initBattleSimulator();
    });

    // Codex Modal
    document.getElementById('btnOpenCodex').addEventListener('click', () => {
      this.openCodexModal('defenses');
    });
    document.getElementById('btnCloseCodex').addEventListener('click', () => {
      document.getElementById('codexModal').classList.add('hidden');
    });
    document.getElementById('btnCloseCodexFooter').addEventListener('click', () => {
      document.getElementById('codexModal').classList.add('hidden');
    });

    const codexFilterBtns = document.querySelectorAll('.codex-filter-btn');
    codexFilterBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        codexFilterBtns.forEach(b => b.classList.remove('btn-gold'));
        btn.classList.add('btn-gold');
        this.renderCodexTable(btn.dataset.type);
      });
    });

    // JSON Export / Import
    document.getElementById('btnJsonExport').addEventListener('click', () => {
      const modal = document.getElementById('jsonModal');
      const ta = document.getElementById('jsonTextArea');
      ta.value = JSON.stringify(this.grid.exportLayout(), null, 2);
      modal.classList.remove('hidden');
    });
    document.getElementById('btnCloseJson').addEventListener('click', () => {
      document.getElementById('jsonModal').classList.add('hidden');
    });
    document.getElementById('btnCopyJson').addEventListener('click', () => {
      const ta = document.getElementById('jsonTextArea');
      navigator.clipboard.writeText(ta.value);
      alert('تم نسخ كود التصميم إلى الحافظة!');
    });
    document.getElementById('btnApplyImport').addEventListener('click', () => {
      const ta = document.getElementById('jsonTextArea');
      try {
        const parsed = JSON.parse(ta.value);
        this.grid.importLayout(parsed);
        this.updateScoreBadge();
        document.getElementById('jsonModal').classList.add('hidden');
      } catch (err) {
        alert('خطأ في صيغة الـ JSON المدخل: ' + err.message);
      }
    });
  }

  setMode(mode) {
    this.mode = mode;
    const btnToggle = document.getElementById('btnToggleMode');
    const scoreboard = document.getElementById('battleScoreboard');
    const dockTabs = document.getElementById('dockTabs');

    if (mode === 'battle') {
      btnToggle.textContent = '🏗️ وضع البناء';
      btnToggle.classList.replace('btn-success', 'btn-gold');
      scoreboard.classList.remove('hidden');
      dockTabs.classList.add('hidden');
      this.initBattleSimulator();
      this.selectedTool = 'giant';
    } else {
      btnToggle.textContent = '⚔️ وضع المعركة';
      btnToggle.classList.replace('btn-gold', 'btn-success');
      scoreboard.classList.add('hidden');
      dockTabs.classList.remove('hidden');
      this.combatSimulator = null;
      this.selectedTool = 'cannon';
    }
    this.renderDockPalette();
  }

  initBattleSimulator() {
    this.combatSimulator = new CombatSimulator(this.grid, []);
    this.updateBattleScoreboard();
  }

  runAutoBenchmark() {
    if (!this.combatSimulator) this.initBattleSimulator();

    // Deploy standard balanced testing army around the village perimeter
    const standardArmy = [
      { type: 'giant', x: 4, y: 10, count: 6 },
      { type: 'wall_breaker', x: 4, y: 12, count: 4 },
      { type: 'wizard', x: 3, y: 8, count: 8 },
      { type: 'pekka', x: 4, y: 18, count: 2 },
      { type: 'barbarian_king', x: 4, y: 22, count: 1 },
      { type: 'archer_queen', x: 4, y: 24, count: 1 },
      { type: 'dragon', x: 20, y: 4, count: 3 },
      { type: 'balloon', x: 22, y: 4, count: 5 }
    ];

    for (const item of standardArmy) {
      for (let i = 0; i < item.count; i++) {
        const jx = item.x + (Math.random() - 0.5) * 2;
        const jy = item.y + (Math.random() - 0.5) * 2;
        this.combatSimulator.deployTroop(item.type, jx, jy, 11, true);
      }
    }
  }

  onMouseDown(e) {
    if (e.button !== 0) return;
    this.isMouseDown = true;
    this.isDragging = false;
    this.dragStart = { x: e.clientX, y: e.clientY };
    this.lastMouse = { x: e.clientX, y: e.clientY };
  }

  onMouseMove(e) {
    if (!this.isMouseDown) return;
    const dx = e.clientX - this.lastMouse.x;
    const dy = e.clientY - this.lastMouse.y;

    if (Math.hypot(e.clientX - this.dragStart.x, e.clientY - this.dragStart.y) > 5) {
      this.isDragging = true;
      this.renderer.originX += dx;
      this.renderer.originY += dy;
    }
    this.lastMouse = { x: e.clientX, y: e.clientY };
  }

  onMouseUp(e) {
    if (!this.isMouseDown) return;
    this.isMouseDown = false;

    // Handle click action if not dragging
    if (!this.isDragging) {
      const rect = this.canvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;
      const gridPos = this.renderer.screenToGrid(clickX, clickY);

      this.handleGridClick(gridPos.x, gridPos.y);
    }
  }

  onWheel(e) {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.15 : 0.85;
    this.renderer.zoom = Math.max(0.4, Math.min(2.5, this.renderer.zoom * factor));
  }

  handleGridClick(gx, gy) {
    if (gx < 0 || gy < 0 || gx >= this.grid.size || gy >= this.grid.size) return;

    if (this.mode === 'battle') {
      // Deploy troop
      if (this.combatSimulator) {
        const deployed = this.combatSimulator.deployTroop(this.selectedTool, gx, gy, this.currentThLevel);
        if (!deployed) {
          // Inside red drop zone
          const card = document.getElementById('scoreBadgeCard');
          if (card) {
            card.style.borderColor = '#e74c3c';
            setTimeout(() => card.style.borderColor = 'var(--panel-border)', 300);
          }
        }
      }
      return;
    }

    // Build mode: inspect or place
    const existingBuilding = this.grid.getBuildingAt(gx, gy);
    if (existingBuilding) {
      this.renderer.selectedBuilding = existingBuilding;
      this.showBuildingHUD(existingBuilding);
      return;
    }

    // Deselect if clicked empty
    this.renderer.selectedBuilding = null;
    this.hideBuildingHUD();

    if (this.selectedTool === 'wall') {
      if (this.grid.getWallAt(gx, gy)) {
        this.grid.removeWall(gx, gy);
      } else {
        this.grid.placeWall(gx, gy, this.currentThLevel);
      }
      this.updateScoreBadge();
    } else if (BUILDING_DEFS[this.selectedTool]) {
      this.grid.placeBuilding(this.selectedTool, gx, gy, this.currentThLevel);
      this.updateScoreBadge();
    }
  }

  showBuildingHUD(b) {
    const hud = document.getElementById('selectedHud');
    const title = document.getElementById('hudName');
    const stats = document.getElementById('hudStats');

    hud.classList.remove('hidden');
    title.textContent = `${b.name} (Lvl ${b.level})`;
    stats.textContent = `نقاط الصحة: ${Math.round(b.hp)} / ${b.maxHp} | الضرر/ثانية: ${b.dps} | المدى: ${b.range} | الحجم: ${b.size}×${b.size}`;
  }

  hideBuildingHUD() {
    document.getElementById('selectedHud').classList.add('hidden');
  }

  renderDockPalette() {
    const container = document.getElementById('paletteItems');
    container.innerHTML = '';

    if (this.mode === 'battle') {
      // Troop deployment deck
      const troopsToShow = [
        'barbarian', 'archer', 'giant', 'wall_breaker', 'balloon',
        'wizard', 'dragon', 'pekka', 'hog_rider', 'barbarian_king', 'archer_queen'
      ];

      for (const tKey of troopsToShow) {
        const tDef = TROOP_DEFS[tKey];
        if (!tDef) continue;

        const card = document.createElement('div');
        card.className = `palette-card ${this.selectedTool === tKey ? 'selected' : ''}`;
        card.innerHTML = `
          <div class="palette-icon" style="background: ${tDef.color};">${tDef.name.substring(0, 2)}</div>
          <div class="palette-name">${tDef.name}</div>
          <div class="palette-size">${tDef.isHero ? 'بطل' : 'جندي'}</div>
        `;
        card.addEventListener('click', () => {
          document.querySelectorAll('.palette-card').forEach(c => c.classList.remove('selected'));
          card.classList.add('selected');
          this.selectedTool = tKey;
        });
        container.appendChild(card);
      }
      return;
    }

    // Build mode items
    let items = [];
    if (this.activeDockCategory === 'defense') {
      items = [
        'cannon', 'archer_tower', 'mortar', 'air_defense', 'wizard_tower',
        'hidden_tesla', 'bomb_tower', 'x_bow', 'inferno_tower', 'eagle_artillery',
        'scattershot', 'spell_tower', 'monolith', 'ricochet_cannon', 'multi_archer_tower'
      ];
    } else if (this.activeDockCategory === 'army_resource') {
      items = [
        'town_hall', 'clan_castle', 'gold_storage', 'elixir_storage', 'dark_elixir_storage',
        'gold_mine', 'elixir_collector', 'army_camp', 'barracks', 'laboratory', 'builder_hut'
      ];
    } else {
      items = ['wall'];
    }

    for (const bKey of items) {
      const bDef = BUILDING_DEFS[bKey];
      if (!bDef) continue;

      const card = document.createElement('div');
      card.className = `palette-card ${this.selectedTool === bKey ? 'selected' : ''}`;
      card.innerHTML = `
        <div class="palette-icon" style="background: ${bDef.color};">${bDef.name.substring(0, 2)}</div>
        <div class="palette-name">${bDef.name}</div>
        <div class="palette-size">${bDef.size}×${bDef.size}</div>
      `;
      card.addEventListener('click', () => {
        document.querySelectorAll('.palette-card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        this.selectedTool = bKey;
      });
      container.appendChild(card);
    }
  }

  updateBattleScoreboard() {
    if (!this.combatSimulator) return;
    const state = this.combatSimulator.getState();

    const starsEl = document.getElementById('battleStars');
    const percentEl = document.getElementById('battlePercent');
    const timerEl = document.getElementById('battleTimer');

    starsEl.textContent = '★'.repeat(state.stars) + '☆'.repeat(3 - state.stars);
    percentEl.textContent = `${state.destructionPercent}%`;

    const remaining = Math.max(0, state.maxTime - state.currentTime);
    const mins = Math.floor(remaining / 60);
    const secs = Math.floor(remaining % 60);
    timerEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  openAnalysisReport() {
    const report = LayoutAnalyzer.evaluateBase(this.grid);
    const modal = document.getElementById('analysisModal');
    const details = document.getElementById('analysisReportDetails');

    details.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 12px; background: #131c27; border-radius: 8px;">
        <span style="font-weight: 700;">النتيجة الدفاعية الإجمالية:</span>
        <span style="font-size: 1.4rem; font-weight: 800; color: var(--coc-gold);">${report.overallScore} / 100</span>
      </div>
      <div style="display: flex; flex-direction: column; gap: 10px; font-size: 0.85rem;">
        <div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
            <span>🛡️ حماية النواة (Town Hall & Core):</span>
            <b>${report.breakdown.coreProtection}%</b>
          </div>
          <div style="height: 6px; background: #2c3e50; border-radius: 3px; overflow: hidden;">
            <div style="height: 100%; width: ${report.breakdown.coreProtection}%; background: #3498db;"></div>
          </div>
        </div>

        <div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
            <span>⚡ تشتيت الصواعق والزلازل (Spell Dispersion):</span>
            <b>${report.breakdown.spellDispersion}%</b>
          </div>
          <div style="height: 6px; background: #2c3e50; border-radius: 3px; overflow: hidden;">
            <div style="height: 100%; width: ${report.breakdown.spellDispersion}%; background: #e74c3c;"></div>
          </div>
        </div>

        <div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
            <span>🎯 توازن التغطية الدفاعية (Coverage Balance):</span>
            <b>${report.breakdown.coverageBalance}%</b>
          </div>
          <div style="height: 6px; background: #2c3e50; border-radius: 3px; overflow: hidden;">
            <div style="height: 100%; width: ${report.breakdown.coverageBalance}%; background: #2ecc71;"></div>
          </div>
        </div>

        <div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
            <span>🧱 جودة الغرف وعزل الجدران:</span>
            <b>${report.breakdown.compartmentalization}%</b>
          </div>
          <div style="height: 6px; background: #2c3e50; border-radius: 3px; overflow: hidden;">
            <div style="height: 100%; width: ${report.breakdown.compartmentalization}%; background: #f1c40f;"></div>
          </div>
        </div>
      </div>
    `;

    modal.classList.remove('hidden');
  }

  openCodexModal(filterType = 'defenses') {
    const modal = document.getElementById('codexModal');
    modal.classList.remove('hidden');
    this.renderCodexTable(filterType);
  }

  renderCodexTable(filterType) {
    const container = document.getElementById('codexContentTable');
    let html = '';

    if (filterType === 'defenses') {
      html = `
        <table class="codex-table">
          <thead>
            <tr>
              <th>المبنى</th>
              <th>الحجم</th>
              <th>المدى</th>
              <th>نوع الهدف</th>
              <th>أعلى مستوى</th>
              <th>نقاط الصحة (Max HP)</th>
              <th>الضرر/ث (Max DPS)</th>
            </tr>
          </thead>
          <tbody>
      `;
      for (const [key, def] of Object.entries(BUILDING_DEFS)) {
        const maxLvl = def.levels[def.levels.length - 1];
        html += `
          <tr>
            <td><b>${def.name}</b></td>
            <td>${def.size}×${def.size}</td>
            <td>${def.range || '-'}</td>
            <td>${def.targetType || 'كلاهما'}</td>
            <td>Lvl ${def.levels.length}</td>
            <td>${maxLvl ? maxLvl.hp : '-'}</td>
            <td>${maxLvl ? (maxLvl.dps || '-') : '-'}</td>
          </tr>
        `;
      }
      html += `</tbody></table>`;
    } else if (filterType === 'troops') {
      html = `
        <table class="codex-table">
          <thead>
            <tr>
              <th>الجندي</th>
              <th>الهدف المفضل</th>
              <th>السرعة</th>
              <th>المساحة</th>
              <th>طيران؟</th>
              <th>أعلى مستوى</th>
              <th>أقصى صحة (Max HP)</th>
            </tr>
          </thead>
          <tbody>
      `;
      for (const [key, tDef] of Object.entries(TROOP_DEFS)) {
        if (tDef.isHero) continue;
        const maxLvl = tDef.levels[tDef.levels.length - 1];
        html += `
          <tr>
            <td><b>${tDef.name}</b></td>
            <td>${tDef.targetPreference}</td>
            <td>${tDef.speed}</td>
            <td>${tDef.housingSpace}</td>
            <td>${tDef.isFlying ? 'نعم' : 'لا'}</td>
            <td>Lvl ${tDef.levels.length}</td>
            <td>${maxLvl ? maxLvl.hp : '-'}</td>
          </tr>
        `;
      }
      html += `</tbody></table>`;
    } else {
      html = `
        <table class="codex-table">
          <thead>
            <tr>
              <th>البطل</th>
              <th>القدرة الخارقة</th>
              <th>المدى</th>
              <th>السرعة</th>
              <th>المستويات المتاحة</th>
              <th>أقصى صحة (Lvl 110)</th>
              <th>أقصى ضرر (Lvl 110)</th>
            </tr>
          </thead>
          <tbody>
      `;
      for (const [key, hDef] of Object.entries(TROOP_DEFS)) {
        if (!hDef.isHero) continue;
        const maxLvl = hDef.levels[hDef.levels.length - 1];
        html += `
          <tr>
            <td><b>${hDef.name}</b></td>
            <td>${hDef.ability ? hDef.ability.name : 'لا يوجد'}</td>
            <td>${hDef.range}</td>
            <td>${hDef.speed}</td>
            <td>${hDef.levels.length} مستويات</td>
            <td>${maxLvl ? maxLvl.hp : '-'}</td>
            <td>${maxLvl ? maxLvl.dps : '-'}</td>
          </tr>
        `;
      }
      html += `</tbody></table>`;
    }

    container.innerHTML = html;
  }

  loop(timestamp) {
    const delta = Math.min(0.1, (timestamp - this.lastTimestamp) / 1000);
    this.lastTimestamp = timestamp;

    if (this.mode === 'battle' && this.combatSimulator) {
      this.combatSimulator.step(delta);
      this.updateBattleScoreboard();
    }

    this.renderer.render(this.grid, this.combatSimulator);
    requestAnimationFrame((t) => this.loop(t));
  }
}

// Start application upon DOM loaded
window.addEventListener('DOMContentLoaded', () => {
  const app = new ClashApp();
  app.init();
});

// Cenários: checklists reutilizáveis (ex.: "receber visitas").

import { list, esc, newUuid, deleteScenario, resetScenario } from '../domain.js';
import { upsert, tombstone } from '../api.js';

let currentScenarioUuid = null;

export async function render(el) {
  const [scenarios, items] = await Promise.all([
    list('scenarios', 'order=name.asc'),
    list('scenario_items', 'order=position.asc'),
  ]);
  const scenario = currentScenarioUuid ? scenarios.find((s) => s.uuid === currentScenarioUuid) : null;
  if (currentScenarioUuid && !scenario) currentScenarioUuid = null;
  if (scenario) renderDetail(el, scenario, items.filter((i) => i.scenario_uuid === scenario.uuid));
  else renderList(el, scenarios, items);
}

function renderList(el, scenarios, items) {
  el.innerHTML = `
    <h1>Cenários</h1>
    ${scenarios.map((s) => {
      const own = items.filter((i) => i.scenario_uuid === s.uuid);
      const done = own.filter((i) => i.checked).length;
      return `
      <button class="neo-card scenario-row" data-scenario="${s.uuid}">
        <strong class="grow">${esc(s.name)}</strong>
        <span class="muted">${done} de ${own.length} feitos</span>
      </button>`;
    }).join('')}
    ${scenarios.length === 0 ? '<p class="empty">Nenhum cenário. Crie um checklist para ocasiões!</p>' : ''}
    <button class="fab" id="add-scenario" title="Novo cenário">＋</button>`;

  for (const row of el.querySelectorAll('[data-scenario]')) {
    row.onclick = () => { currentScenarioUuid = row.dataset.scenario; render(el); };
  }
  el.querySelector('#add-scenario').onclick = async () => {
    const name = prompt('Nome do cenário:');
    if (!name?.trim()) return;
    try {
      const uuid = newUuid();
      await upsert('scenarios', [{ uuid, name: name.trim(), deleted: false }]);
      currentScenarioUuid = uuid;
      await render(el);
    } catch (e) { alert(e.message); }
  };
}

function renderDetail(el, scenario, items) {
  el.innerHTML = `
    <div class="detail-header">
      <button class="link-btn" id="back">← Cenários</button>
      <h1>${esc(scenario.name)}</h1>
      <div>
        <button class="neo-btn-secondary" id="rename">Renomear</button>
        <button class="neo-btn-secondary" id="reset">Resetar</button>
        <button class="neo-btn-secondary" id="del">Excluir</button>
      </div>
    </div>
    ${items.map((i) => `
      <div class="neo-card item-row">
        <label class="grow check-row">
          <input type="checkbox" class="neo-check" data-check="${i.uuid}" ${i.checked ? 'checked' : ''}>
          <span class="${i.checked ? 'strike' : ''}">${esc(i.title)}</span>
        </label>
        <button class="link-btn" data-del-item="${i.uuid}">✕</button>
      </div>`).join('')}
    <form class="add-item-row" id="add-item-form">
      <input class="neo-input grow" id="new-item" placeholder="Novo item…" required>
      <button class="neo-btn" type="submit">Adicionar</button>
    </form>`;

  el.querySelector('#back').onclick = () => { currentScenarioUuid = null; render(el); };
  el.querySelector('#rename').onclick = async () => {
    const name = prompt('Novo nome:', scenario.name);
    if (!name?.trim()) return;
    try { await upsert('scenarios', [{ ...scenario, name: name.trim() }]); await render(el); }
    catch (e) { alert(e.message); }
  };
  el.querySelector('#reset').onclick = async () => {
    if (!confirm('Desmarcar todos os itens?')) return;
    try { await resetScenario(items); await render(el); }
    catch (e) { alert(e.message); }
  };
  el.querySelector('#del').onclick = async () => {
    if (!confirm(`Excluir o cenário "${scenario.name}"?`)) return;
    try { await deleteScenario(scenario); currentScenarioUuid = null; await render(el); }
    catch (e) { alert(e.message); }
  };
  for (const box of el.querySelectorAll('[data-check]')) {
    box.onchange = async () => {
      const item = items.find((i) => i.uuid === box.dataset.check);
      try { await upsert('scenario_items', [{ ...item, checked: box.checked }]); await render(el); }
      catch (e) { alert(e.message); }
    };
  }
  for (const btn of el.querySelectorAll('[data-del-item]')) {
    btn.onclick = async () => {
      try { await tombstone('scenario_items', [btn.dataset.delItem]); await render(el); }
      catch (e) { alert(e.message); }
    };
  }
  el.querySelector('#add-item-form').onsubmit = async (ev) => {
    ev.preventDefault();
    const title = el.querySelector('#new-item').value.trim();
    if (!title) return;
    const maxPos = items.reduce((max, i) => Math.max(max, i.position), 0);
    try {
      await upsert('scenario_items', [{
        uuid: newUuid(), scenario_uuid: scenario.uuid,
        title, checked: false, position: maxPos + 1, deleted: false,
      }]);
      await render(el);
    } catch (e) { alert(e.message); }
  };
}

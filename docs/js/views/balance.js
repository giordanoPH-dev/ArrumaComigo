// Balanço: conclusões por pessoa nesta semana + histórico recente.

import { list, esc } from '../domain.js';
import { todayStr, addDays, isoDowOf } from '../recurrence.js';

export async function render(el) {
  const today = todayStr();
  const monday = addDays(today, -(isoDowOf(today) - 1));
  const [people, weekCompletions, recent] = await Promise.all([
    list('people'),
    list('task_completions',
      `completed_at=gte.${monday}T00:00:00&completed_at=lt.${addDays(monday, 7)}T00:00:00`),
    list('task_completions', 'order=completed_at.desc&limit=100'),
  ]);

  const personByUuid = Object.fromEntries(people.map((p) => [p.uuid, p]));
  const countByPerson = {};
  for (const c of weekCompletions) {
    const key = c.person_uuid ?? '';
    countByPerson[key] = (countByPerson[key] ?? 0) + 1;
  }

  const fmtWhen = (iso) =>
    `${iso.slice(8, 10)}/${iso.slice(5, 7)} ${iso.slice(11, 16)}`;

  el.innerHTML = `
    <h1>Balanço</h1>
    <div class="neo-card">
      <h2>Esta semana</h2>
      ${people.map((p) => `
        <div class="balance-row">
          <span class="avatar" style="background:${esc(p.color_hex)}">${p.emoji}</span>
          <span class="grow">${esc(p.name)}</span>
          <strong>${countByPerson[p.uuid] ?? 0}</strong>
        </div>`).join('')}
      ${countByPerson[''] ? `
        <div class="balance-row">
          <span class="avatar">🤷</span><span class="grow">Sem responsável</span>
          <strong>${countByPerson['']}</strong>
        </div>` : ''}
      <div class="balance-row balance-total">
        <span class="grow">Total</span><strong>${weekCompletions.length}</strong>
      </div>
    </div>
    <div class="neo-card">
      <h2>Recentes</h2>
      ${recent.length === 0 ? '<p class="empty">Nenhuma conclusão ainda.</p>' : ''}
      ${recent.map((c) => {
        const person = c.person_uuid ? personByUuid[c.person_uuid] : null;
        return `
        <div class="balance-row">
          <span class="avatar" ${person ? `style="background:${esc(person.color_hex)}"` : ''}>${person ? person.emoji : '🤷'}</span>
          <span class="grow">${esc(c.task_title)}</span>
          <span class="muted">${fmtWhen(c.completed_at)}</span>
        </div>`;
      }).join('')}
    </div>`;
}

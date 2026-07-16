// Aba Hoje: paridade com o TodayScreen.kt do Android —
// cabeçalho com progresso por pessoa, abas da semana (hoje vira "Hoje"),
// linha de filtros (estado/pessoa/cômodo), agrupamento por pessoa e menu ⊘ no card.

import { list, esc, roomType, DAY_LABELS,
  completeTask, uncompleteTask, skipTask, postponeTask, reassignTask, byPosition, reorder } from '../domain.js';
import { occursOn, progressFraction, todayStr, addDays } from '../recurrence.js';

let selectedDate = null;      // aba escolhida; null = hoje
let selPerson = null;         // uuid da pessoa no filtro; null = todos
let selRoom = null;           // uuid do cômodo no filtro; null = todos
let doneFilter = 'ALL';       // ALL | PENDING | DONE

const parseD = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const fmtWeekday = (s) => new Intl.DateTimeFormat('pt-BR', { weekday: 'short' }).format(parseD(s));
const fmtMonth = (s) => new Intl.DateTimeFormat('pt-BR', { month: 'short' }).format(parseD(s));

// Rótulo de vencimento: Atrasada / Hoje / Amanhã / "15 de jul." (espelho de DateLabels.kt).
const dueLabel = (date, today) => {
  if (date < today) return 'Atrasada';
  if (date === today) return 'Hoje';
  if (date === addDays(today, 1)) return 'Amanhã';
  return `${parseD(date).getDate()} de ${fmtMonth(date)}`;
};

export async function render(el) {
  const today = todayStr();
  const [y, m, d] = today.split('-').map(Number);
  const isoDow = ((new Date(y, m - 1, d).getDay() + 6) % 7) + 1;
  const monday = addDays(today, -(isoDow - 1));
  const week = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
  const date = selectedDate && week.includes(selectedDate) ? selectedDate : today;
  selectedDate = date;
  const isToday = date === today;
  const isPast = date < today;

  const [tasks, rooms, people, weekCompletions] = await Promise.all([
    list('tasks'),
    list('rooms'),
    list('people'),
    list('task_completions',
      `completed_at=gte.${monday}T00:00:00&completed_at=lt.${addDays(monday, 7)}T00:00:00`),
  ]);

  const roomByUuid = Object.fromEntries(rooms.map((r) => [r.uuid, r]));
  const personByUuid = Object.fromEntries(people.map((p) => [p.uuid, p]));
  const taskByUuid = Object.fromEntries(tasks.map((t) => [t.uuid, t]));

  const completionsOnDay = weekCompletions.filter((c) => c.completed_at.slice(0, 10) === date);
  const doneByTask = Object.fromEntries(completionsOnDay.map((c) => [c.task_uuid, c]));

  const pending = tasks.filter((t) =>
    !t.is_archived && !doneByTask[t.uuid] &&
    occursOn(t.next_due_date, t.recurrence, t.recurrence_interval, t.days_of_week, date, today))
    .sort(byPosition);

  // Conjunto do dia (pendentes + concluídas), sem filtros — base do progresso do cabeçalho.
  const dayCards = [
    ...pending.map((t) => ({ task: t, done: false, completion: null })),
    ...completionsOnDay.map((c) => (taskByUuid[c.task_uuid]
      ? { task: taskByUuid[c.task_uuid], done: true, completion: c } : null)).filter(Boolean),
  ];

  // Filtros de pessoa, cômodo e estado.
  const visible = dayCards.filter(({ task, done }) =>
    (selPerson === null || task.assigned_person_uuid === selPerson) &&
    (selRoom === null || task.room_uuid === selRoom) &&
    (doneFilter === 'ALL' || (doneFilter === 'PENDING' ? !done : done)));

  // ---------- Cabeçalho ----------
  const title = isToday ? 'Hoje' : cap(new Intl.DateTimeFormat('pt-BR', { weekday: 'long' }).format(parseD(date)));
  const subtitle = `${fmtWeekday(date)}, ${parseD(date).getDate()} de ${fmtMonth(date)}`;
  const progressParts = people.map((p) => {
    const theirs = dayCards.filter((c) => c.task.assigned_person_uuid === p.uuid);
    return theirs.length ? `${p.emoji} ${theirs.filter((c) => c.done).length}/${theirs.length}` : null;
  }).filter(Boolean);
  const rightInfo = progressParts.length ? progressParts.join(' · ')
    : isPast ? `${dayCards.filter((c) => c.done).length} feitas`
      : dayCards.length === 0 ? '✨' : `${dayCards.filter((c) => !c.done).length} pendentes`;

  // ---------- Abas da semana ----------
  const tabs = week.map((day, i) => {
    const label = day === today ? 'Hoje' : `${DAY_LABELS[i]} ${day.slice(8, 10)}`;
    return `<button class="day-chip ${day === date ? 'sel' : ''}" data-date="${day}">${label}</button>`;
  }).join('');

  // ---------- Filtros (selects estilizados como chips) ----------
  const doneOpts = { ALL: 'Todas', PENDING: 'Pendentes', DONE: 'Concluídas' };
  const doneSel = `<select class="filter-chip ${doneFilter !== 'ALL' ? 'on' : ''}" data-filter="done">
    ${Object.entries(doneOpts).map(([v, t]) =>
      `<option value="${v}" ${doneFilter === v ? 'selected' : ''}>${t}</option>`).join('')}
  </select>`;
  const personSel = `<select class="filter-chip ${selPerson ? 'on' : ''}" data-filter="person">
    <option value="" ${!selPerson ? 'selected' : ''}>👥 Todos</option>
    ${people.map((p) =>
      `<option value="${p.uuid}" ${selPerson === p.uuid ? 'selected' : ''}>${p.emoji} ${esc(p.name)}</option>`).join('')}
  </select>`;
  const roomSel = `<select class="filter-chip ${selRoom ? 'on' : ''}" data-filter="room">
    <option value="" ${!selRoom ? 'selected' : ''}>🏠 Todos</option>
    ${rooms.map((r) =>
      `<option value="${r.uuid}" ${selRoom === r.uuid ? 'selected' : ''}>${roomType(r.type).emoji} ${esc(r.name)}</option>`).join('')}
  </select>`;

  // ---------- Agrupamento por pessoa ----------
  const pendingFirst = (arr) => [...arr].sort((a, b) => Number(a.done) - Number(b.done));
  let groups;
  if (selPerson) {
    const name = personByUuid[selPerson]?.name ?? 'Tarefas';
    groups = [{ label: name, items: pendingFirst(visible) }];
  } else {
    groups = people
      .map((p) => ({ label: p.name, items: pendingFirst(visible.filter((c) => c.task.assigned_person_uuid === p.uuid)) }))
      .filter((g) => g.items.length);
    const unassigned = pendingFirst(visible.filter((c) => !c.task.assigned_person_uuid));
    if (unassigned.length) groups.push({ label: 'Sem responsável', items: unassigned });
  }

  // ---------- Card ----------
  const card = (c, groupPending, idx) => {
    const { task, done } = c;
    const room = roomByUuid[task.room_uuid];
    const person = task.assigned_person_uuid ? personByUuid[task.assigned_person_uuid] : null;
    const labelDate = isToday ? task.next_due_date : date;
    const overdue = !done && labelDate < today;
    const frac = progressFraction(task.next_due_date, task.recurrence, task.recurrence_interval, task.days_of_week);
    const canMoveUp = !done && idx > 0;
    const canMoveDown = !done && idx < groupPending.length - 1;
    const menuItems = [
      isToday ? `<button data-postpone="${task.uuid}">Adiar para amanhã</button>` : '',
      isToday ? `<button data-skip="${task.uuid}">Pular esta ocorrência</button>` : '',
      canMoveUp ? `<button data-move="${idx}" data-to="${idx - 1}">Mover para cima</button>` : '',
      canMoveDown ? `<button data-move="${idx}" data-to="${idx + 1}">Mover para baixo</button>` : '',
    ].filter(Boolean).join('');

    return `
    <div class="neo-card task-card ${done ? 'done' : ''}">
      <div class="card-row">
        <label class="task-main">
          <input type="checkbox" class="neo-check" data-toggle="${task.uuid}" ${done ? 'checked' : ''}>
          <div class="task-info">
            <div class="task-title">${esc(task.title)}</div>
            <div class="task-meta">
              ${room ? `<span>${roomType(room.type).emoji} ${esc(room.name)}</span>` : ''}
              <span class="due ${overdue ? 'overdue' : ''}">${done ? dueLabel(task.next_due_date, today) : dueLabel(labelDate, today)}</span>
              ${task.recurrence !== 'NONE' ? '<span class="meta-ico" title="Recorrente">↻</span>' : ''}
              ${task.reminder_enabled ? '<span class="meta-ico" title="Lembrete">🔔</span>' : ''}
            </div>
            ${!done && task.recurrence !== 'NONE'
              ? `<div class="progress"><div class="progress-fill ${overdue ? 'over' : ''}" style="width:${Math.round(frac * 100)}%"></div></div>`
              : ''}
          </div>
        </label>
        <div class="task-side">
          <button class="avatar-btn" data-person="${task.uuid}" ${!isPast ? '' : 'disabled'} title="Trocar responsável">
            ${person
              ? `<span class="avatar" style="background:${esc(person.color_hex)}">${person.emoji}</span>`
              : '<span class="avatar avatar-empty">👤</span>'}
          </button>
          ${menuItems ? `
          <div class="menu-wrap">
            <button class="icon-btn" data-menu title="Ações da tarefa" aria-label="Ações da tarefa">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="9"></circle><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
            <div class="menu">${menuItems}</div>
          </div>` : ''}
        </div>
      </div>
    </div>`;
  };

  // ---------- Monta a tela ----------
  const groupsHtml = groups.map((g) => {
    const groupPending = g.items.filter((c) => !c.done).map((c) => c.task);
    let pi = -1;
    const cards = g.items.map((c) => {
      const idx = c.done ? -1 : (++pi);
      return card(c, groupPending, idx);
    }).join('');
    return `<h2 class="group-header">${esc(g.label)}</h2>${cards}`;
  }).join('');

  const empty = isPast ? '🗓️' : '🧹';
  const emptyMsg = isPast ? 'Nada registrado neste dia.'
    : isToday ? 'Nenhuma tarefa por aqui!' : 'Nada agendado para este dia 🌿';

  el.innerHTML = `
    <div class="day-header">
      <div class="day-title"><h1>${title}</h1><span class="day-sub">· ${subtitle}</span></div>
      <span class="day-progress">${rightInfo}</span>
    </div>
    <div class="day-row">${tabs}</div>
    <div class="filters">${doneSel}${personSel}${roomSel}</div>
    ${visible.length === 0
      ? `<div class="empty-day"><div class="empty-emoji">${empty}</div><p>${emptyMsg}</p>${isToday ? '<p class="muted">Ajuste os filtros ou aproveite o descanso.</p>' : ''}</div>`
      : groupsHtml}`;

  wireUp(el, { taskByUuid, doneByTask, groups, date, people });
}

// ---------- Interação ----------
function wireUp(el, { taskByUuid, doneByTask, groups, date, people }) {
  const rerender = () => render(el);
  const busy = new Set();
  const act = (uuid, fn) => async () => {
    if (busy.has(uuid)) return;
    busy.add(uuid);
    try { await fn(); await rerender(); } catch (e) { alert(e.message); busy.delete(uuid); }
  };

  for (const btn of el.querySelectorAll('.day-chip')) {
    btn.onclick = () => { selectedDate = btn.dataset.date; rerender(); };
  }

  el.querySelector('[data-filter="done"]').onchange = (e) => { doneFilter = e.target.value; rerender(); };
  el.querySelector('[data-filter="person"]').onchange = (e) => { selPerson = e.target.value || null; rerender(); };
  el.querySelector('[data-filter="room"]').onchange = (e) => { selRoom = e.target.value || null; rerender(); };

  for (const box of el.querySelectorAll('[data-toggle]')) {
    const uuid = box.dataset.toggle;
    const task = taskByUuid[uuid];
    const completion = doneByTask[uuid];
    box.onchange = act(uuid, () => completion ? uncompleteTask(completion, task) : completeTask(task, date));
  }
  for (const btn of el.querySelectorAll('[data-skip]')) {
    btn.onclick = act(btn.dataset.skip, () => skipTask(taskByUuid[btn.dataset.skip]));
  }
  for (const btn of el.querySelectorAll('[data-postpone]')) {
    btn.onclick = act(btn.dataset.postpone, () => postponeTask(taskByUuid[btn.dataset.postpone]));
  }

  // Reordenar dentro do grupo (só pendentes). Cada grupo tem sua própria lista de pendentes.
  const groupPendingTasks = groups.map((g) => g.items.filter((c) => !c.done).map((c) => c.task));
  for (const btn of el.querySelectorAll('[data-move]')) {
    btn.onclick = async () => {
      const wrap = btn.closest('.neo-card');
      // Descobre a qual grupo o card pertence pela ordem no DOM.
      const gi = groupIndexOf(el, wrap);
      const from = Number(btn.dataset.move);
      const to = Number(btn.dataset.to);
      try { await reorder('tasks', groupPendingTasks[gi] ?? [], from, to); await rerender(); }
      catch (e) { alert(e.message); }
    };
  }

  // Menus (⊘ e troca de responsável): abre/fecha popover.
  for (const btn of el.querySelectorAll('[data-menu]')) {
    btn.onclick = (e) => { e.stopPropagation(); toggleMenu(btn.parentElement.querySelector('.menu')); };
  }
  for (const btn of el.querySelectorAll('[data-person]')) {
    if (btn.disabled) continue;
    btn.onclick = (e) => {
      e.stopPropagation();
      openPersonMenu(btn, taskByUuid[btn.dataset.person], people, rerender);
    };
  }
}

// Índice do grupo a partir do card no DOM (conta os h2 de grupo antes dele).
function groupIndexOf(el, cardEl) {
  let gi = -1;
  for (const node of el.children) {
    if (node.classList?.contains('group-header')) gi++;
    if (node === cardEl) return gi;
  }
  return gi;
}

let openMenu = null;
function toggleMenu(menu) {
  if (openMenu && openMenu !== menu) openMenu.classList.remove('open');
  const willOpen = !menu.classList.contains('open');
  menu.classList.toggle('open', willOpen);
  openMenu = willOpen ? menu : null;
}
function closeMenus() { if (openMenu) { openMenu.classList.remove('open'); openMenu = null; } }

// Popover de troca de responsável, construído sob demanda ao lado do avatar.
function openPersonMenu(btn, task, people, rerender) {
  const wrap = btn.closest('.task-side');
  let menu = wrap.querySelector('.menu.person-menu');
  if (!menu) {
    const items = [`<button data-set="">Sem responsável</button>`]
      .concat(people.map((p) => `<button data-set="${p.uuid}">${p.emoji} ${esc(p.name)}</button>`)).join('');
    menu = document.createElement('div');
    menu.className = 'menu person-menu';
    menu.innerHTML = items;
    wrap.appendChild(menu);
    for (const b of menu.querySelectorAll('[data-set]')) {
      b.onclick = async (e) => {
        e.stopPropagation();
        closeMenus();
        try { await reassignTask(task, b.dataset.set || null); await rerender(); } catch (err) { alert(err.message); }
      };
    }
  }
  toggleMenu(menu);
}

// Fecha menus ao tocar fora — registrado uma única vez.
if (!window.__acTodayMenuInit) {
  window.__acTodayMenuInit = true;
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.menu-wrap') && !e.target.closest('.task-side')) closeMenus();
  });
}

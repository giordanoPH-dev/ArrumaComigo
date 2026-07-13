// Aba Hoje: calendário semanal seg→dom com abas por dia (semântica do TodayViewModel.kt).

import { list, esc, roomType, PRIORITIES, DAY_LABELS,
  completeTask, uncompleteTask, skipTask, postponeTask } from '../domain.js';
import { occursOn, progressFraction, todayStr, addDays } from '../recurrence.js';

let selectedDate = null; // aba escolhida; null = hoje

const fmtShort = (dateStr) => `${dateStr.slice(8, 10)}/${dateStr.slice(5, 7)}`;

export async function render(el) {
  const today = todayStr();
  // Segunda-feira da semana atual: isoDow 1..7 → volta (isoDow-1) dias.
  const [y, m, d] = today.split('-').map(Number);
  const isoDow = ((new Date(y, m - 1, d).getDay() + 6) % 7) + 1;
  const monday = addDays(today, -(isoDow - 1));
  const week = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
  const date = selectedDate && week.includes(selectedDate) ? selectedDate : today;
  selectedDate = date;

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

  // Feitas no dia: completed_at cai na aba selecionada.
  const completionsOnDay = weekCompletions.filter((c) => c.completed_at.slice(0, 10) === date);
  const doneByTask = Object.fromEntries(completionsOnDay.map((c) => [c.task_uuid, c]));

  const pending = tasks.filter((t) =>
    !t.is_archived && !doneByTask[t.uuid] &&
    occursOn(t.next_due_date, t.recurrence, t.recurrence_interval, t.days_of_week, date, today));

  const tabs = week.map((day, i) => `
    <button class="day-tab ${day === date ? 'chip-on' : ''}" data-date="${day}">
      <span>${DAY_LABELS[i]}</span><strong>${day.slice(8, 10)}</strong>
      ${day === today ? '<em>hoje</em>' : ''}
    </button>`).join('');

  const card = (task, completion) => {
    const room = roomByUuid[task.room_uuid];
    const person = task.assigned_person_uuid ? personByUuid[task.assigned_person_uuid] : null;
    const done = Boolean(completion);
    const overdue = date === today && task.next_due_date < today;
    const dueLabel = date === today
      ? (overdue ? 'Atrasada' : fmtShort(task.next_due_date))
      : fmtShort(date);
    const frac = progressFraction(task.next_due_date, task.recurrence,
      task.recurrence_interval, task.days_of_week);
    return `
    <div class="neo-card task-card ${done ? 'done' : ''}">
      <label class="task-main">
        <input type="checkbox" class="neo-check" data-toggle="${task.uuid}" ${done ? 'checked' : ''}>
        <div class="task-info">
          <div class="task-title">${room ? roomType(room.type).emoji : '🏠'} ${esc(task.title)}</div>
          <div class="task-meta">
            ${room ? `<span>${esc(room.name)}</span>` : ''}
            <span class="prio prio-${task.priority}">${PRIORITIES[task.priority] ?? task.priority}</span>
            <span class="${overdue ? 'overdue' : ''}">${dueLabel}</span>
            ${task.estimated_minutes ? `<span>${task.estimated_minutes} min</span>` : ''}
          </div>
          ${!done && task.recurrence !== 'NONE'
            ? `<div class="progress"><div class="progress-fill" style="width:${Math.round(frac * 100)}%"></div></div>`
            : ''}
        </div>
        ${person ? `<span class="avatar" style="background:${esc(person.color_hex)}" title="${esc(person.name)}">${person.emoji}</span>` : ''}
      </label>
      ${!done ? `
      <div class="task-actions">
        <button class="link-btn" data-skip="${task.uuid}">Pular</button>
        <button class="link-btn" data-postpone="${task.uuid}">Adiar</button>
      </div>` : ''}
    </div>`;
  };

  el.innerHTML = `
    <div class="week-tabs">${tabs}</div>
    ${pending.length + completionsOnDay.length === 0
      ? '<p class="empty">Nada por aqui. Dia livre! 🎉</p>'
      : pending.map((t) => card(t, null)).join('') +
        completionsOnDay.map((c) => taskByUuid[c.task_uuid] ? card(taskByUuid[c.task_uuid], c) : '').join('')}`;

  for (const btn of el.querySelectorAll('.day-tab')) {
    btn.onclick = () => { selectedDate = btn.dataset.date; render(el); };
  }
  const busy = new Set();
  const act = (uuid, fn) => async () => {
    if (busy.has(uuid)) return;
    busy.add(uuid);
    try { await fn(); await render(el); }
    catch (e) { alert(e.message); busy.delete(uuid); }
  };
  for (const box of el.querySelectorAll('[data-toggle]')) {
    const uuid = box.dataset.toggle;
    const task = taskByUuid[uuid];
    const completion = doneByTask[uuid];
    box.onchange = act(uuid, () => completion
      ? uncompleteTask(completion, task)
      : completeTask(task, date));
  }
  for (const btn of el.querySelectorAll('[data-skip]')) {
    btn.onclick = act(btn.dataset.skip, () => skipTask(taskByUuid[btn.dataset.skip]));
  }
  for (const btn of el.querySelectorAll('[data-postpone]')) {
    btn.onclick = act(btn.dataset.postpone, () => postponeTask(taskByUuid[btn.dataset.postpone]));
  }
}

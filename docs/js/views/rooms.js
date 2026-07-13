// Cômodos: grid → detalhe com tarefas → form completo de tarefa.

import { list, esc, newUuid, roomType, ROOM_TYPES, RECURRENCES, PRIORITIES, DAY_LABELS,
  deleteRoom, deleteTask, byPosition, reorder } from '../domain.js';
import { upsert } from '../api.js';
import { firstWeeklyOccurrence, todayStr } from '../recurrence.js';

let currentRoomUuid = null;

export async function render(el) {
  const [rooms, tasks, people] = await Promise.all([list('rooms'), list('tasks'), list('people')]);
  const room = currentRoomUuid ? rooms.find((r) => r.uuid === currentRoomUuid) : null;
  if (currentRoomUuid && !room) currentRoomUuid = null;
  if (room) renderDetail(el, room, tasks, people, rooms);
  else renderGrid(el, rooms, tasks);
}

function renderGrid(el, rooms, tasks) {
  const activeCount = (r) => tasks.filter((t) => t.room_uuid === r.uuid && !t.is_archived).length;
  el.innerHTML = `
    <h1>Cômodos</h1>
    <div class="room-grid">
      ${rooms.map((r) => `
        <button class="neo-card room-card" data-room="${r.uuid}">
          <span class="room-emoji">${roomType(r.type).emoji}</span>
          <strong>${esc(r.name)}</strong>
          <span class="muted">${activeCount(r)} tarefa(s)</span>
        </button>`).join('')}
    </div>
    ${rooms.length === 0 ? '<p class="empty">Nenhum cômodo ainda. Crie o primeiro!</p>' : ''}
    <button class="fab" id="add-room" title="Novo cômodo">＋</button>`;

  for (const card of el.querySelectorAll('[data-room]')) {
    card.onclick = () => { currentRoomUuid = card.dataset.room; render(el); };
  }
  el.querySelector('#add-room').onclick = () => roomDialog(el, null);
}

function renderDetail(el, room, tasks, people, rooms) {
  const roomTasks = tasks.filter((t) => t.room_uuid === room.uuid && !t.is_archived).sort(byPosition);
  const personByUuid = Object.fromEntries(people.map((p) => [p.uuid, p]));
  el.innerHTML = `
    <div class="detail-header">
      <button class="link-btn" id="back">← Cômodos</button>
      <h1>${roomType(room.type).emoji} ${esc(room.name)}</h1>
      <div>
        <button class="neo-btn-secondary" id="edit-room">Editar</button>
        <button class="neo-btn-secondary" id="del-room">Excluir</button>
      </div>
    </div>
    ${roomTasks.map((t, i) => {
      const person = t.assigned_person_uuid ? personByUuid[t.assigned_person_uuid] : null;
      return `
      <div class="row-with-move">
        <button class="neo-card task-row" data-task="${t.uuid}">
          <div class="task-info">
            <div class="task-title">${esc(t.title)}</div>
            <div class="task-meta">
              <span>${RECURRENCES[t.recurrence] ?? t.recurrence}</span>
              <span class="prio prio-${t.priority}">${PRIORITIES[t.priority] ?? t.priority}</span>
              <span>vence ${t.next_due_date.slice(8, 10)}/${t.next_due_date.slice(5, 7)}</span>
            </div>
          </div>
          ${person ? `<span class="avatar" style="background:${esc(person.color_hex)}" title="${esc(person.name)}">${person.emoji}</span>` : ''}
        </button>
        <div class="move-col">
          <button class="move-btn" data-move-up="${i}" title="Mover para cima" ${i === 0 ? 'disabled' : ''}>▲</button>
          <button class="move-btn" data-move-down="${i}" title="Mover para baixo" ${i === roomTasks.length - 1 ? 'disabled' : ''}>▼</button>
        </div>
      </div>`;
    }).join('')}
    ${roomTasks.length === 0 ? '<p class="empty">Sem tarefas neste cômodo.</p>' : ''}
    <button class="fab" id="add-task" title="Nova tarefa">＋</button>`;

  el.querySelector('#back').onclick = () => { currentRoomUuid = null; render(el); };
  el.querySelector('#edit-room').onclick = () => roomDialog(el, room);
  el.querySelector('#del-room').onclick = async () => {
    if (!confirm(`Excluir "${room.name}" e todas as suas tarefas?`)) return;
    try { await deleteRoom(room); currentRoomUuid = null; await render(el); }
    catch (e) { alert(e.message); }
  };
  for (const row of el.querySelectorAll('[data-task]')) {
    row.onclick = () => taskDialog(el, roomTasks.find((t) => t.uuid === row.dataset.task), rooms, people, room);
  }
  const move = (from, to) => async () => {
    try { await reorder('tasks', roomTasks, from, to); await render(el); }
    catch (e) { alert(e.message); }
  };
  for (const btn of el.querySelectorAll('[data-move-up]')) {
    const i = Number(btn.dataset.moveUp);
    btn.onclick = move(i, i - 1);
  }
  for (const btn of el.querySelectorAll('[data-move-down]')) {
    const i = Number(btn.dataset.moveDown);
    btn.onclick = move(i, i + 1);
  }
  el.querySelector('#add-task').onclick = () => taskDialog(el, null, rooms, people, room);
}

// ---------- Dialog de cômodo ----------

function roomDialog(el, room) {
  const dialog = document.createElement('dialog');
  dialog.innerHTML = `
    <form method="dialog" class="dialog-form" id="room-form">
      <h2>${room ? 'Editar cômodo' : 'Novo cômodo'}</h2>
      <label>Nome
        <input class="neo-input" id="room-name" required value="${room ? esc(room.name) : ''}">
      </label>
      <label>Tipo
        <select class="neo-input" id="room-type">
          ${Object.entries(ROOM_TYPES).map(([key, v]) =>
            `<option value="${key}" ${room?.type === key ? 'selected' : ''}>${v.emoji} ${v.label}</option>`).join('')}
        </select>
      </label>
      <div class="dialog-actions">
        <button class="neo-btn-secondary" type="button" id="cancel">Cancelar</button>
        <button class="neo-btn" type="submit">Salvar</button>
      </div>
    </form>`;
  document.body.appendChild(dialog);
  dialog.addEventListener('close', () => dialog.remove());
  dialog.querySelector('#cancel').onclick = () => dialog.close();
  dialog.querySelector('#room-form').onsubmit = async (ev) => {
    ev.preventDefault();
    try {
      await upsert('rooms', [{
        ...(room ?? { uuid: newUuid(), deleted: false }),
        name: dialog.querySelector('#room-name').value.trim(),
        type: dialog.querySelector('#room-type').value,
      }]);
      dialog.close();
      await render(el);
    } catch (e) { alert(e.message); }
  };
  dialog.showModal();
}

// ---------- Dialog de tarefa ----------

function taskDialog(el, task, rooms, people, defaultRoom) {
  const dialog = document.createElement('dialog');
  const dow = task ? task.days_of_week : 0;
  dialog.innerHTML = `
    <form method="dialog" class="dialog-form" id="task-form">
      <h2>${task ? 'Editar tarefa' : 'Nova tarefa'}</h2>
      <label>Título
        <input class="neo-input" id="t-title" required value="${task ? esc(task.title) : ''}">
      </label>
      <label>Cômodo
        <select class="neo-input" id="t-room">
          ${rooms.map((r) => `<option value="${r.uuid}"
            ${(task ? task.room_uuid : defaultRoom.uuid) === r.uuid ? 'selected' : ''}>
            ${roomType(r.type).emoji} ${esc(r.name)}</option>`).join('')}
        </select>
      </label>
      <label>Responsável
        <select class="neo-input" id="t-person">
          <option value="">Ninguém</option>
          ${people.map((p) => `<option value="${p.uuid}"
            ${task?.assigned_person_uuid === p.uuid ? 'selected' : ''}>${p.emoji} ${esc(p.name)}</option>`).join('')}
        </select>
      </label>
      <span class="field-label">Prioridade</span>
      <div class="chip-row" id="t-priority">
        ${Object.entries(PRIORITIES).map(([key, label]) =>
          `<button type="button" class="chip ${(task?.priority ?? 'MEDIUM') === key ? 'chip-on' : ''}"
             data-prio="${key}">${label}</button>`).join('')}
      </div>
      <label>Recorrência
        <select class="neo-input" id="t-recurrence">
          ${Object.entries(RECURRENCES).map(([key, label]) =>
            `<option value="${key}" ${(task?.recurrence ?? 'NONE') === key ? 'selected' : ''}>${label}</option>`).join('')}
        </select>
      </label>
      <label id="t-interval-wrap">Intervalo (a cada N)
        <input class="neo-input" type="number" id="t-interval" min="1" value="${task?.recurrence_interval ?? 1}">
      </label>
      <div class="chip-row" id="t-days" hidden>
        ${DAY_LABELS.map((label, i) =>
          `<button type="button" class="chip ${(dow & (1 << i)) ? 'chip-on' : ''}" data-day="${i}">${label}</button>`).join('')}
      </div>
      <label>Próximo vencimento
        <input class="neo-input" type="date" id="t-due" required value="${task?.next_due_date ?? todayStr()}">
      </label>
      <label>Duração estimada (min)
        <input class="neo-input" type="number" id="t-minutes" min="1" value="${task?.estimated_minutes ?? ''}">
      </label>
      <label>Lembrete
        <input class="neo-input" type="time" id="t-reminder" value="${task?.reminder_time ? task.reminder_time.slice(0, 5) : ''}">
      </label>
      <label class="check-row">
        <input type="checkbox" id="t-reminder-on" ${task?.reminder_enabled ? 'checked' : ''}> Lembrete ativado
      </label>
      <p class="muted">Lembretes tocam no tablet.</p>
      <div class="dialog-actions">
        ${task ? '<button class="neo-btn-secondary" type="button" id="t-delete">Excluir</button>' : ''}
        <button class="neo-btn-secondary" type="button" id="cancel">Cancelar</button>
        <button class="neo-btn" type="submit">Salvar</button>
      </div>
    </form>`;
  document.body.appendChild(dialog);
  dialog.addEventListener('close', () => dialog.remove());
  dialog.querySelector('#cancel').onclick = () => dialog.close();

  let priority = task?.priority ?? 'MEDIUM';
  for (const chip of dialog.querySelectorAll('[data-prio]')) {
    chip.onclick = () => {
      priority = chip.dataset.prio;
      dialog.querySelectorAll('[data-prio]').forEach((c) => c.classList.toggle('chip-on', c === chip));
    };
  }
  let daysOfWeek = dow;
  for (const chip of dialog.querySelectorAll('[data-day]')) {
    chip.onclick = () => {
      daysOfWeek ^= 1 << Number(chip.dataset.day);
      chip.classList.toggle('chip-on');
    };
  }
  const recurrenceSelect = dialog.querySelector('#t-recurrence');
  const syncDays = () => {
    dialog.querySelector('#t-days').hidden = recurrenceSelect.value !== 'WEEKLY';
  };
  recurrenceSelect.onchange = syncDays;
  syncDays();

  if (task) {
    dialog.querySelector('#t-delete').onclick = async () => {
      if (!confirm(`Excluir a tarefa "${task.title}"?`)) return;
      try { await deleteTask(task); dialog.close(); await render(el); }
      catch (e) { alert(e.message); }
    };
  }

  dialog.querySelector('#task-form').onsubmit = async (ev) => {
    ev.preventDefault();
    const recurrence = recurrenceSelect.value;
    const effectiveDays = recurrence === 'WEEKLY' ? daysOfWeek : (task?.days_of_week ?? 0);
    let dueDate = dialog.querySelector('#t-due').value;
    // Semanal nova: primeira ocorrência cai no primeiro dia marcado (não nasce atrasada).
    if (!task && recurrence === 'WEEKLY') dueDate = firstWeeklyOccurrence(dueDate, effectiveDays);
    const reminder = dialog.querySelector('#t-reminder').value;
    const minutes = dialog.querySelector('#t-minutes').value;
    try {
      await upsert('tasks', [{
        ...(task ?? { uuid: newUuid(), is_archived: false, deleted: false, position: 0 }),
        title: dialog.querySelector('#t-title').value.trim(),
        room_uuid: dialog.querySelector('#t-room').value,
        assigned_person_uuid: dialog.querySelector('#t-person').value || null,
        priority,
        recurrence,
        recurrence_interval: Math.max(1, Number(dialog.querySelector('#t-interval').value) || 1),
        days_of_week: effectiveDays,
        next_due_date: dueDate,
        estimated_minutes: minutes ? Number(minutes) : null,
        reminder_time: reminder || null,
        reminder_enabled: dialog.querySelector('#t-reminder-on').checked,
      }]);
      dialog.close();
      await render(el);
    } catch (e) { alert(e.message); }
  };
  dialog.showModal();
}

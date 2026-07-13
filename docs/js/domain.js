// Enums de UI, leituras e mutações de domínio — espelho do OfflineHouseholdRepository.kt.

import { get, upsert, tombstone, uuid } from './api.js';
import { next, firstWeeklyOccurrence, addDays, todayStr } from './recurrence.js';

// ---------- Enums ----------

export const ROOM_TYPES = {
  KITCHEN: { label: 'Cozinha', emoji: '🍳' },
  BATHROOM: { label: 'Banheiro', emoji: '🛁' },
  BEDROOM: { label: 'Quarto', emoji: '🛏️' },
  LIVING_ROOM: { label: 'Sala', emoji: '🛋️' },
  LAUNDRY: { label: 'Lavanderia', emoji: '🧺' },
  KIDS_ROOM: { label: 'Quarto das crianças', emoji: '🧸' },
  OFFICE: { label: 'Escritório', emoji: '💻' },
  OUTDOOR: { label: 'Área externa', emoji: '🪴' },
  GARAGE: { label: 'Garagem', emoji: '🚗' },
  OTHER: { label: 'Outro', emoji: '🏠' },
};
export const roomType = (type) => ROOM_TYPES[type] ?? ROOM_TYPES.OTHER;

export const RECURRENCES = { NONE: 'Uma vez', DAILY: 'Diária', WEEKLY: 'Semanal', MONTHLY: 'Mensal' };
export const PRIORITIES = { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta' };
export const DAY_LABELS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

export const PERSON_COLORS = [
  '#6C4DDB', '#9C8CF0', '#E5739D', '#F0A35C',
  '#52B6A4', '#5C9CE5', '#C77DD6', '#E5B84D',
];
export const PERSON_EMOJIS = ['🙂', '😎', '🧑', '👩', '👨', '👧', '👦', '🐱', '🐶', '🌟', '🦄', '🍀'];

// ---------- Helpers ----------

export const esc = (s) => String(s ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

export const newUuid = uuid;

/** Linhas vivas de uma tabela. extra = filtros PostgREST adicionais. */
export const list = (table, extra = '') => get(`${table}?deleted=eq.false${extra ? '&' + extra : ''}`);

const nowTime = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

const maxDate = (a, b) => (a > b ? a : b);

/** Ordena por position ASC (sort estável: empate mantém a ordem atual da lista). */
export const byPosition = (a, b) => (a.position ?? 0) - (b.position ?? 0);

/** Move o item do índice from para to na lista visível e renumera (position = índice).
 *  Upsert só das linhas cuja position mudou.
 *  ponytail: renumerar um subconjunto (ex.: só pendentes do Hoje) pode reordenar
 *  globalmente itens fora dele — aceito. */
export async function reorder(table, items, from, to) {
  if (to < 0 || to >= items.length || from === to) return;
  const arr = [...items];
  arr.splice(to, 0, arr.splice(from, 1)[0]);
  const changed = arr
    .map((it, i) => ({ ...it, position: i }))
    .filter((it, i) => (arr[i].position ?? 0) !== i);
  if (changed.length > 0) await upsert(table, changed);
}

// ---------- Tarefas ----------

/** Conclui no dia da aba: registra completion e avança next_due_date (ou arquiva). */
export async function completeTask(task, dateStr) {
  await upsert('task_completions', [{
    uuid: uuid(),
    task_uuid: task.uuid,
    person_uuid: task.assigned_person_uuid ?? null,
    task_title: task.title,
    completed_at: `${dateStr}T${nowTime()}`,
    due_date: task.next_due_date,
    deleted: false,
  }]);
  const from = maxDate(task.next_due_date, dateStr);
  const n = next(from, task.recurrence, task.recurrence_interval, task.days_of_week);
  await upsert('tasks', [n === null
    ? { ...task, is_archived: true }
    : { ...task, next_due_date: n }]);
}

/** Desfaz uma conclusão: tombstona a completion e restaura a data da tarefa. */
export async function uncompleteTask(completion, task) {
  await tombstone('task_completions', [completion.uuid]);
  await upsert('tasks', [{
    ...task,
    next_due_date: completion.due_date ?? task.next_due_date,
    is_archived: false,
  }]);
}

/** Pula a ocorrência atual (sem registrar conclusão). */
export async function skipTask(task) {
  const from = maxDate(task.next_due_date, todayStr());
  const n = next(from, task.recurrence, task.recurrence_interval, task.days_of_week);
  await upsert('tasks', [n === null
    ? { ...task, is_archived: true }
    : { ...task, next_due_date: n }]);
}

/** Adia para amanhã (semanal com dias marcados: próximo dia do padrão). */
export async function postponeTask(task) {
  const tomorrow = addDays(todayStr(), 1);
  const target = task.recurrence === 'WEEKLY' && task.days_of_week !== 0
    ? firstWeeklyOccurrence(tomorrow, task.days_of_week)
    : tomorrow;
  await upsert('tasks', [{ ...task, next_due_date: target }]);
}

/** Exclui tarefa + suas completions (cascata do CASCADE local do Android). */
export async function deleteTask(task) {
  const completions = await get(`task_completions?task_uuid=eq.${task.uuid}&deleted=eq.false&select=uuid`);
  await tombstone('task_completions', completions.map((c) => c.uuid));
  await tombstone('tasks', [task.uuid]);
}

// ---------- Cômodos ----------

/** Exclui cômodo + tasks do cômodo + completions dessas tasks. */
export async function deleteRoom(room) {
  const tasks = await get(`tasks?room_uuid=eq.${room.uuid}&deleted=eq.false&select=uuid`);
  const taskUuids = tasks.map((t) => t.uuid);
  if (taskUuids.length > 0) {
    const completions = await get(
      `task_completions?task_uuid=in.(${taskUuids.join(',')})&deleted=eq.false&select=uuid`);
    await tombstone('task_completions', completions.map((c) => c.uuid));
  }
  await tombstone('tasks', taskUuids);
  await tombstone('rooms', [room.uuid]);
}

// ---------- Pessoas ----------

/** Espelho do deletePerson do Kotlin: só a pessoa vira tombstone; referências penduradas
 *  (assigned_person_uuid, person_uuid) são tratadas como "sem responsável" na leitura. */
export async function deletePerson(person) {
  await tombstone('people', [person.uuid]);
}

// ---------- Cenários ----------

/** Exclui cenário + seus itens. */
export async function deleteScenario(scenario) {
  const items = await get(`scenario_items?scenario_uuid=eq.${scenario.uuid}&deleted=eq.false&select=uuid`);
  await tombstone('scenario_items', items.map((i) => i.uuid));
  await tombstone('scenarios', [scenario.uuid]);
}

/** Desmarca todos os itens marcados. */
export async function resetScenario(items) {
  const checked = items.filter((i) => i.checked);
  if (checked.length === 0) return;
  await upsert('scenario_items', checked.map((i) => ({ ...i, checked: false })));
}

// Porta 1:1 do RecurrenceCalculator.kt.
// API pública trabalha com strings "YYYY-MM-DD" (nunca new Date('YYYY-MM-DD'):
// o parse UTC desloca o dia em GMT-3). Bitmask: bit 0 = segunda ... bit 6 = domingo.

const parse = (s) => {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
};

const fmt = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

// ISO day-of-week: 1 = segunda ... 7 = domingo.
const isoDow = (d) => ((d.getDay() + 6) % 7) + 1;

const plusDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

// java.time plusMonths: clampa no fim do mês (31/jan + 1m = 28-29/fev).
// O Date nativo transborda (31/jan + 1m = 2-3/mar), então é manual.
const plusMonths = (d, n) => {
  const total = d.getMonth() + n;
  const y = d.getFullYear() + Math.floor(total / 12);
  const m = ((total % 12) + 12) % 12;
  const lastDay = new Date(y, m + 1, 0).getDate();
  return new Date(y, m, Math.min(d.getDate(), lastDay));
};

export const isDaySelected = (isoDay, daysOfWeek) => (daysOfWeek & (1 << (isoDay - 1))) !== 0;

export const toggleDay = (daysOfWeek, isoDay) => daysOfWeek ^ (1 << (isoDay - 1));

export const addDays = (dateStr, n) => fmt(plusDays(parse(dateStr), n));

export const todayStr = () => fmt(new Date());

export const isoDowOf = (dateStr) => isoDow(parse(dateStr));

/** Próxima data após from ("YYYY-MM-DD"), ou null para NONE. */
export function next(from, recurrence, interval = 1, daysOfWeek = 0) {
  const step = Math.max(1, interval);
  const d = parse(from);
  switch (recurrence) {
    case 'NONE': return null;
    case 'DAILY': return fmt(plusDays(d, step));
    case 'MONTHLY': return fmt(plusMonths(d, step));
    case 'WEEKLY': return fmt(nextWeekly(d, step, daysOfWeek));
    default: return null;
  }
}

function nextWeekly(from, interval, daysOfWeek) {
  if (daysOfWeek === 0) return plusDays(from, interval * 7);
  for (let offset = 1; offset <= 7; offset++) {
    const candidate = plusDays(from, offset);
    if (isDaySelected(isoDow(candidate), daysOfWeek)) {
      const crossedWeek = isoDow(candidate) <= isoDow(from);
      return crossedWeek ? plusDays(candidate, (interval - 1) * 7) : candidate;
    }
  }
  return plusDays(from, interval * 7);
}

/** Ocorrência anterior a from, ou null para NONE. */
export function previous(from, recurrence, interval = 1, daysOfWeek = 0) {
  const step = Math.max(1, interval);
  const d = parse(from);
  switch (recurrence) {
    case 'NONE': return null;
    case 'DAILY': return fmt(plusDays(d, -step));
    case 'MONTHLY': return fmt(plusMonths(d, -step));
    case 'WEEKLY': return fmt(previousWeekly(d, step, daysOfWeek));
    default: return null;
  }
}

function previousWeekly(from, interval, daysOfWeek) {
  if (daysOfWeek === 0) return plusDays(from, -interval * 7);
  for (let offset = 1; offset <= 7; offset++) {
    const candidate = plusDays(from, -offset);
    if (isDaySelected(isoDow(candidate), daysOfWeek)) {
      const crossedWeek = isoDow(candidate) >= isoDow(from);
      return crossedWeek ? plusDays(candidate, -(interval - 1) * 7) : candidate;
    }
  }
  return plusDays(from, -interval * 7);
}

/** Primeira ocorrência de uma semanal a partir de from: evita nascer "atrasada". */
export function firstWeeklyOccurrence(from, daysOfWeek) {
  const d = parse(from);
  if (daysOfWeek === 0 || isDaySelected(isoDow(d), daysOfWeek)) return from;
  for (let offset = 1; offset <= 6; offset++) {
    const candidate = plusDays(d, offset);
    if (isDaySelected(isoDow(candidate), daysOfWeek)) return fmt(candidate);
  }
  return from;
}

/** A tarefa ocorre em date? Em today as atrasadas acumulam; futuro segue o padrão. */
export function occursOn(nextDueDate, recurrence, interval, daysOfWeek, date, today) {
  if (date < today) return false;
  if (date === today) return nextDueDate <= today;
  let occurrence = nextDueDate > today ? nextDueDate : today;
  while (occurrence < date) {
    occurrence = next(occurrence, recurrence, interval, daysOfWeek);
    if (occurrence === null) return false;
  }
  return occurrence === date;
}

/** Fração [0..1] do período decorrida — barra de progresso do cartão. now = Date. */
export function progressFraction(nextDueDate, recurrence, interval, daysOfWeek, now = new Date()) {
  // ponytail: tarefa avulsa não tem período; janela fixa de 7 dias antes do vencimento.
  const start = previous(nextDueDate, recurrence, interval, daysOfWeek) ?? addDays(nextDueDate, -7);
  const startAt = parse(start).getTime();
  const endAt = plusDays(parse(nextDueDate), 1).getTime();
  const total = endAt - startAt;
  if (total <= 0) return 1;
  return Math.min(1, Math.max(0, (now.getTime() - startAt) / total));
}

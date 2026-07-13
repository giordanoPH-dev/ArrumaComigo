// Porte dos casos do RecurrenceCalculatorTest.kt. Rodar: node docs/test/recurrence.test.mjs
import assert from 'node:assert/strict';
import {
  next, previous, firstWeeklyOccurrence, occursOn, progressFraction,
  isDaySelected, toggleDay,
} from '../js/recurrence.js';

const monday = '2026-06-22'; // segunda-feira
// Bitmask: bit 0 = segunda ... bit 6 = domingo.
const MON = 1, TUE = 2, WED = 4, THU = 8, FRI = 16;

// none has no next date
assert.equal(next(monday, 'NONE'), null);

// daily advances by interval days
assert.equal(next(monday, 'DAILY'), '2026-06-23');
assert.equal(next(monday, 'DAILY', 3), '2026-06-25');

// monthly advances by interval months
assert.equal(next(monday, 'MONTHLY'), '2026-07-22');
assert.equal(next(monday, 'MONTHLY', 2), '2026-08-22');

// monthly clampa no fim do mês (java.time plusMonths)
assert.equal(next('2026-01-31', 'MONTHLY'), '2026-02-28');
assert.equal(next('2026-03-31', 'MONTHLY'), '2026-04-30');
assert.equal(next('2027-12-31', 'MONTHLY', 2), '2028-02-29'); // ano bissexto

// weekly without days adds a week
assert.equal(next(monday, 'WEEKLY'), '2026-06-29');

// weekly finds next selected weekday
assert.equal(next(monday, 'WEEKLY', 1, WED), '2026-06-24');

// biweekly single day advances two weeks
assert.equal(next(monday, 'WEEKLY', 2, MON), '2026-07-06');

// biweekly keeps remaining days of the active week
assert.equal(next(monday, 'WEEKLY', 2, MON | THU), '2026-06-25'); // quinta da mesma semana
assert.equal(next('2026-06-25', 'WEEKLY', 2, MON | THU), '2026-07-06'); // pula 1 semana extra

// monthly with interval three advances a quarter
assert.equal(next(monday, 'MONTHLY', 3), '2026-09-22');

// weekly single day same weekday advances one week
assert.equal(next(monday, 'WEEKLY', 1, MON), '2026-06-29');

// first weekly occurrence keeps date when day matches or no days set
assert.equal(firstWeeklyOccurrence(monday, MON), monday);
assert.equal(firstWeeklyOccurrence(monday, 0), monday);

// first weekly occurrence advances to the next selected day
assert.equal(firstWeeklyOccurrence('2026-06-24', TUE), '2026-06-30'); // quarta → terça seguinte

// occursOn accumulates overdue only on today
const today = '2026-06-24'; // quarta
assert.equal(occursOn(monday, 'WEEKLY', 1, 0, today, today), true);
assert.equal(occursOn(monday, 'WEEKLY', 1, 0, '2026-06-23', today), false);

// occursOn projects future occurrences following the pattern
const dueFriday = '2026-06-26';
assert.equal(occursOn(dueFriday, 'WEEKLY', 1, FRI, dueFriday, monday), true);
assert.equal(occursOn(dueFriday, 'WEEKLY', 1, FRI, '2026-06-25', monday), false);
assert.equal(occursOn(monday, 'DAILY', 1, 0, '2026-06-25', monday), true);
assert.equal(occursOn('2026-06-24', 'NONE', 1, 0, '2026-06-24', monday), true);
assert.equal(occursOn('2026-06-24', 'NONE', 1, 0, '2026-06-25', monday), false);

// previous mirrors next for simple recurrences
assert.equal(previous(monday, 'NONE'), null);
assert.equal(previous(monday, 'DAILY'), '2026-06-21');
assert.equal(previous(monday, 'DAILY', 3), '2026-06-19');
assert.equal(previous(monday, 'MONTHLY', 2), '2026-04-22');
assert.equal(previous(monday, 'WEEKLY'), '2026-06-15');

// previous weekly finds the prior selected day
assert.equal(previous('2026-06-26', 'WEEKLY', 1, MON | FRI), monday); // sexta → segunda da mesma semana
assert.equal(previous(monday, 'WEEKLY', 1, MON | FRI), '2026-06-19'); // segunda → sexta anterior
assert.equal(previous(monday, 'WEEKLY', 1, MON), '2026-06-15'); // semanal na segunda: -7d
assert.equal(previous(monday, 'WEEKLY', 2, MON), '2026-06-08'); // quinzenal na segunda: -14d

// progressFraction fills over the period and saturates when overdue
const at = (y, m, d, h = 0, min = 0) => new Date(y, m - 1, d, h, min);
const frac = (now) => progressFraction(monday, 'WEEKLY', 1, MON, now);
assert.ok(Math.abs(frac(at(2026, 6, 15)) - 0) < 0.01); // logo após a anterior: vazia
assert.ok(Math.abs(frac(at(2026, 6, 19)) - 0.5) < 0.01); // meio da janela de 8 dias
const onDueDay = frac(at(2026, 6, 22, 12, 0));
assert.ok(onDueDay >= 0.85 && onDueDay <= 0.99); // no dia: quase cheia
assert.equal(frac(at(2026, 6, 24, 9, 0)), 1); // atrasada: cheia

// toggle and isDaySelected are consistent
let mask = 0;
mask = toggleDay(mask, 5); // sexta (isoDow 5)
assert.equal(isDaySelected(5, mask), true);
mask = toggleDay(mask, 5);
assert.equal(isDaySelected(5, mask), false);

console.log('recurrence.test.mjs: todos os asserts passaram ✔');

// Pessoas: lista + form (nome, emoji, cor).

import { list, esc, newUuid, PERSON_COLORS, PERSON_EMOJIS, deletePerson } from '../domain.js';
import { upsert } from '../api.js';

export async function render(el) {
  const people = await list('people');
  el.innerHTML = `
    <h1>Pessoas</h1>
    ${people.map((p) => `
      <div class="neo-card person-row">
        <span class="avatar avatar-lg" style="background:${esc(p.color_hex)}">${p.emoji}</span>
        <strong class="grow">${esc(p.name)}</strong>
        <button class="neo-btn-secondary" data-edit="${p.uuid}">Editar</button>
        <button class="neo-btn-secondary" data-del="${p.uuid}">Excluir</button>
      </div>`).join('')}
    ${people.length === 0 ? '<p class="empty">Ninguém ainda. Adicione as pessoas da casa!</p>' : ''}
    <button class="fab" id="add-person" title="Nova pessoa">＋</button>`;

  for (const btn of el.querySelectorAll('[data-edit]')) {
    btn.onclick = () => personDialog(el, people.find((p) => p.uuid === btn.dataset.edit));
  }
  for (const btn of el.querySelectorAll('[data-del]')) {
    btn.onclick = async () => {
      const person = people.find((p) => p.uuid === btn.dataset.del);
      if (!confirm(`Excluir ${person.name}? As tarefas dela(e) ficam sem responsável.`)) return;
      try { await deletePerson(person); await render(el); }
      catch (e) { alert(e.message); }
    };
  }
  el.querySelector('#add-person').onclick = () => personDialog(el, null);
}

function personDialog(el, person) {
  let emoji = person?.emoji ?? PERSON_EMOJIS[0];
  let color = person?.color_hex ?? PERSON_COLORS[0];
  const dialog = document.createElement('dialog');
  dialog.innerHTML = `
    <form method="dialog" class="dialog-form" id="person-form">
      <h2>${person ? 'Editar pessoa' : 'Nova pessoa'}</h2>
      <label>Nome
        <input class="neo-input" id="p-name" required value="${person ? esc(person.name) : ''}">
      </label>
      <span class="field-label">Emoji</span>
      <div class="pick-grid">
        ${PERSON_EMOJIS.map((e) =>
          `<button type="button" class="pick ${e === emoji ? 'chip-on' : ''}" data-emoji="${e}">${e}</button>`).join('')}
      </div>
      <span class="field-label">Cor</span>
      <div class="pick-grid">
        ${PERSON_COLORS.map((c) =>
          `<button type="button" class="pick color-pick ${c === color ? 'chip-on' : ''}"
             data-color="${c}" style="background:${c}"></button>`).join('')}
      </div>
      <div class="dialog-actions">
        <button class="neo-btn-secondary" type="button" id="cancel">Cancelar</button>
        <button class="neo-btn" type="submit">Salvar</button>
      </div>
    </form>`;
  document.body.appendChild(dialog);
  dialog.addEventListener('close', () => dialog.remove());
  dialog.querySelector('#cancel').onclick = () => dialog.close();

  for (const btn of dialog.querySelectorAll('[data-emoji]')) {
    btn.onclick = () => {
      emoji = btn.dataset.emoji;
      dialog.querySelectorAll('[data-emoji]').forEach((b) => b.classList.toggle('chip-on', b === btn));
    };
  }
  for (const btn of dialog.querySelectorAll('[data-color]')) {
    btn.onclick = () => {
      color = btn.dataset.color;
      dialog.querySelectorAll('[data-color]').forEach((b) => b.classList.toggle('chip-on', b === btn));
    };
  }
  dialog.querySelector('#person-form').onsubmit = async (ev) => {
    ev.preventDefault();
    try {
      await upsert('people', [{
        ...(person ?? { uuid: newUuid(), deleted: false }),
        name: dialog.querySelector('#p-name').value.trim(),
        emoji,
        color_hex: color,
      }]);
      dialog.close();
      await render(el);
    } catch (e) { alert(e.message); }
  };
  dialog.showModal();
}

// Família: código de convite, membros e saída (rota #familia).

import { get, rpc, getHousehold, getUserId, clearSession } from '../api.js';
import { esc } from '../domain.js';

const rerender = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

export async function render(el) {
  const [[household], members] = await Promise.all([
    get(`households?select=name,invite_code&id=eq.${getHousehold()}`),
    get('household_members?select=user_id,name'),
  ]);
  const me = getUserId();

  el.innerHTML = `
    <div class="neo-card">
      <h2>👪 ${esc(household?.name ?? 'Família')}</h2>
      <p>Convide quem mora com você com este código:</p>
      <p class="invite-code">${esc(household?.invite_code ?? '')}</p>
      <div class="add-item-row">
        <button class="neo-btn" id="copy">Copiar</button>
        ${navigator.share ? '<button class="neo-btn-secondary" id="share">Compartilhar</button>' : ''}
      </div>
    </div>
    <div class="neo-card">
      <h2>Membros</h2>
      ${members.map((m) => `
        <div class="person-row">
          <span class="grow">${esc(m.name ?? 'Sem nome')}${m.user_id === me ? ' <strong>(você)</strong>' : ''}</span>
          ${m.user_id === me ? '' : `<button class="neo-btn-secondary" data-remove="${esc(m.user_id)}" data-name="${esc(m.name ?? '')}">Remover</button>`}
        </div>`).join('')}
    </div>
    <p class="error" id="family-error" hidden></p>
    <div class="add-item-row">
      <button class="neo-btn-secondary" id="leave">Sair da família</button>
      <button class="neo-btn-secondary" id="logout">Sair da conta</button>
    </div>`;

  const error = el.querySelector('#family-error');
  const fail = (e) => { error.textContent = e.message; error.hidden = false; };

  el.querySelector('#copy').onclick = async (ev) => {
    try {
      await navigator.clipboard.writeText(household.invite_code);
      ev.target.textContent = 'Copiado!';
      setTimeout(() => { ev.target.textContent = 'Copiar'; }, 2000);
    } catch (e) { fail(e); }
  };

  const share = el.querySelector('#share');
  if (share) {
    share.onclick = () => navigator.share({
      text: `Entre na nossa família no Arruma Comigo com o código ${household.invite_code}`,
    }).catch(() => { /* usuário cancelou */ });
  }

  for (const btn of el.querySelectorAll('[data-remove]')) {
    btn.onclick = async () => {
      if (!confirm(`Remover ${btn.dataset.name || 'este membro'} da família?`)) return;
      try {
        await rpc('remove_member', { p_user_id: btn.dataset.remove });
        await render(el);
      } catch (e) { fail(e); }
    };
  }

  el.querySelector('#leave').onclick = async () => {
    if (!confirm('Sair da família? Você vai precisar de um código para voltar.')) return;
    try {
      await rpc('remove_member', { p_user_id: me });
      localStorage.removeItem('ac_household');
      location.hash = '';
      rerender();
    } catch (e) { fail(e); }
  };

  el.querySelector('#logout').onclick = () => { clearSession(); rerender(); };
}

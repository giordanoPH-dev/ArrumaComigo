// Login/registro (GoTrue) e escolha de família (RPCs create_household / join_household).

import { getSession, signIn, signUp, clearSession, setHousehold, fetchMembership, rpc } from '../api.js';
import { esc } from '../domain.js';

const rerender = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

export async function render(el) {
  if (!getSession()) renderAuth(el);
  else renderFamily(el);
}

function renderAuth(el) {
  let mode = 'login'; // 'login' | 'signup'
  el.innerHTML = `
    <div class="auth-wrap">
      <h1 class="app-title">🧹 Arruma Comigo</h1>
      <form class="neo-card auth-card" id="auth-form">
        <h2 id="auth-title">Entrar</h2>
        <input class="neo-input" type="email" id="email" placeholder="E-mail" required autocomplete="email">
        <input class="neo-input" type="password" id="password" placeholder="Senha" required
               minlength="6" autocomplete="current-password">
        <p class="error" id="auth-error" hidden></p>
        <button class="neo-btn" type="submit" id="auth-submit">Entrar</button>
        <button class="neo-btn-secondary" type="button" id="auth-toggle">Criar uma conta</button>
      </form>
    </div>`;

  const error = el.querySelector('#auth-error');
  el.querySelector('#auth-toggle').onclick = () => {
    mode = mode === 'login' ? 'signup' : 'login';
    el.querySelector('#auth-title').textContent = mode === 'login' ? 'Entrar' : 'Criar conta';
    el.querySelector('#auth-submit').textContent = mode === 'login' ? 'Entrar' : 'Registrar';
    el.querySelector('#auth-toggle').textContent = mode === 'login' ? 'Criar uma conta' : 'Já tenho conta';
    error.hidden = true;
  };
  el.querySelector('#auth-form').onsubmit = async (ev) => {
    ev.preventDefault();
    error.hidden = true;
    const email = el.querySelector('#email').value.trim();
    const password = el.querySelector('#password').value;
    try {
      if (mode === 'login') {
        await signIn(email, password);
      } else {
        const session = await signUp(email, password);
        if (!session) {
          error.textContent = 'Conta criada! Confirme o e-mail e depois entre.';
          error.hidden = false;
          return;
        }
      }
      // Já é membro de uma família? Pula a tela de família.
      const householdId = await fetchMembership().catch(() => null);
      if (householdId) setHousehold(householdId);
      rerender();
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
    }
  };
}

function renderFamily(el) {
  el.innerHTML = `
    <div class="auth-wrap">
      <h1 class="app-title">🧹 Arruma Comigo</h1>
      <form class="neo-card auth-card" id="join-form">
        <h2>Entrar numa família</h2>
        <input class="neo-input" id="join-code" placeholder="Código de convite" required>
        <button class="neo-btn" type="submit">Entrar na família</button>
      </form>
      <form class="neo-card auth-card" id="create-form">
        <h2>Criar uma família</h2>
        <input class="neo-input" id="create-name" placeholder="Nome da família" required>
        <button class="neo-btn" type="submit">Criar família</button>
      </form>
      <div class="neo-card auth-card" id="invite-card" hidden>
        <h2>Família criada!</h2>
        <p>Compartilhe este código com quem mora com você:</p>
        <p class="invite-code" id="invite-code"></p>
        <button class="neo-btn" id="invite-continue">Continuar</button>
      </div>
      <p class="error" id="family-error" hidden></p>
      <button class="neo-btn-secondary" id="logout">Sair</button>
    </div>`;

  const error = el.querySelector('#family-error');
  const fail = (e) => { error.textContent = e.message; error.hidden = false; };

  el.querySelector('#join-form').onsubmit = async (ev) => {
    ev.preventDefault();
    error.hidden = true;
    try {
      const result = await rpc('join_household', { p_code: el.querySelector('#join-code').value.trim() });
      setHousehold(result.id);
      rerender();
    } catch (e) { fail(e); }
  };

  el.querySelector('#create-form').onsubmit = async (ev) => {
    ev.preventDefault();
    error.hidden = true;
    try {
      const result = await rpc('create_household', { p_name: el.querySelector('#create-name').value.trim() });
      setHousehold(result.id);
      el.querySelector('#invite-code').textContent = esc(result.invite_code);
      el.querySelector('#join-form').hidden = true;
      el.querySelector('#create-form').hidden = true;
      el.querySelector('#invite-card').hidden = false;
    } catch (e) { fail(e); }
  };

  el.querySelector('#invite-continue').onclick = rerender;
  el.querySelector('#logout').onclick = () => { clearSession(); rerender(); };
}

// Login/registro (GoTrue) e escolha de família (RPCs create_household / join_household).

import { getSession, signIn, signUp, clearSession, setHousehold, fetchMembership, rpc } from '../api.js';
import { esc } from '../domain.js';

const rerender = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

export async function render(el) {
  if (!getSession()) renderAuth(el);
  else renderFamily(el);
}

function renderAuth(el) {
  el.innerHTML = `
    <div class="auth-wrap">
      <h1 class="app-title">🧹 Arruma Comigo</h1>
      <form class="neo-card auth-card" id="auth-form">
        <h2>Entrar</h2>
        <input class="neo-input" type="email" id="email" placeholder="E-mail" required autocomplete="email">
        <input class="neo-input" type="password" id="password" placeholder="Senha" required
               autocomplete="current-password">
        <p class="error" id="auth-error" hidden></p>
        <button class="neo-btn" type="submit">Entrar</button>
        <button class="neo-btn-secondary" type="button" id="go-signup">Criar uma conta</button>
      </form>
    </div>`;

  const error = el.querySelector('#auth-error');
  el.querySelector('#go-signup').onclick = () => renderSignup(el);
  el.querySelector('#auth-form').onsubmit = async (ev) => {
    ev.preventDefault();
    error.hidden = true;
    try {
      await signIn(el.querySelector('#email').value.trim(), el.querySelector('#password').value);
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

function renderSignup(el) {
  el.innerHTML = `
    <div class="auth-wrap">
      <h1 class="app-title">🧹 Arruma Comigo</h1>
      <form class="neo-card auth-card" id="signup-form">
        <h2>Criar conta</h2>
        <input class="neo-input" id="name" placeholder="Nome" required autocomplete="name">
        <input class="neo-input" type="email" id="email" placeholder="E-mail" required autocomplete="email">
        <input class="neo-input" type="password" id="password" placeholder="Senha" required
               minlength="6" autocomplete="new-password">
        <input class="neo-input" id="invite" placeholder="Código da família (opcional)"
               style="text-transform: uppercase" autocomplete="off">
        <p class="error" id="signup-error" hidden></p>
        <button class="neo-btn" type="submit">Criar conta</button>
        <button class="neo-btn-secondary" type="button" id="go-login">Já tenho conta</button>
      </form>
    </div>`;

  const error = el.querySelector('#signup-error');
  el.querySelector('#go-login').onclick = () => renderAuth(el);
  el.querySelector('#signup-form').onsubmit = async (ev) => {
    ev.preventDefault();
    error.hidden = true;
    const code = el.querySelector('#invite').value.trim().toUpperCase();
    try {
      const session = await signUp(
        el.querySelector('#email').value.trim(),
        el.querySelector('#password').value,
        el.querySelector('#name').value.trim(),
      );
      if (!session) {
        // Confirmação de e-mail ligada: sem sessão ainda.
        el.querySelector('#signup-form').innerHTML = `
          <h2>Criar conta</h2>
          <p>Conta criada! Confirme o e-mail e depois entre.</p>
          <button class="neo-btn" type="button" id="go-login2">Ir para Entrar</button>`;
        el.querySelector('#go-login2').onclick = () => renderAuth(el);
        return;
      }
      if (code) {
        const result = await rpc('join_household', { p_code: code });
        setHousehold(result.id);
      }
      rerender(); // com família → app; sem → renderFamily
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

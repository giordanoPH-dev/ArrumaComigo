// Router por hash + gate de sessão/família.

import { getSession, getHousehold } from './api.js';
import * as login from './views/login.js';
import * as today from './views/today.js';
import * as rooms from './views/rooms.js';
import * as people from './views/people.js';
import * as balance from './views/balance.js';
import * as scenarios from './views/scenarios.js';
import * as family from './views/family.js';

const routes = { hoje: today, comodos: rooms, pessoas: people, balanco: balance, cenarios: scenarios, familia: family };

const view = document.getElementById('view');
const nav = document.getElementById('nav');

async function render() {
  // Gate: sem sessão → login; sem família → tela de família (a mesma view decide).
  if (!getSession() || !getHousehold()) {
    nav.hidden = true;
    view.innerHTML = '';
    try { await login.render(view); } catch (e) { showError(e); }
    return;
  }
  nav.hidden = false;
  const name = routes[location.hash.slice(1)] ? location.hash.slice(1) : 'hoje';
  for (const a of nav.querySelectorAll('a')) {
    a.classList.toggle('active', a.getAttribute('href') === '#' + name);
  }
  view.innerHTML = '<p class="loading">Carregando…</p>';
  try { await routes[name].render(view); } catch (e) { showError(e); }
}

function showError(e) {
  view.innerHTML = `<div class="neo-card error-card"><p>${e.message}</p>
    <button class="neo-btn" id="retry">Tentar de novo</button></div>`;
  view.querySelector('#retry').onclick = render;
}

window.addEventListener('hashchange', render);
render();

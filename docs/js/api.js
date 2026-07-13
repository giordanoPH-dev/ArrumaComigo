// Cliente Supabase (GoTrue + PostgREST) — REST puro, sem SDK. Online-only.

export const BASE = 'https://azzhlkxumbyrgdqtoiqb.supabase.co';
// ponytail: anon key em constante, espelho do SyncConfig.kt do app Android.
export const ANON_KEY =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6emhsa3h1bWJ5cmdkcXRvaXFiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM0NDQzMDQsImV4cCI6MjA5OTAyMDMwNH0.q0QKNkiv176wC7Z2U1aX7qD5B6WsjRAarzgzV2o1aqg';

export const uuid = () => crypto.randomUUID().replaceAll('-', '');

// ---------- Sessão (localStorage) ----------

export function getSession() {
  try { return JSON.parse(localStorage.getItem('ac_session')); } catch { return null; }
}

function saveSession(tokens) {
  const session = {
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    expires_at: Math.floor(Date.now() / 1000) + tokens.expires_in,
  };
  localStorage.setItem('ac_session', JSON.stringify(session));
  return session;
}

export function clearSession() {
  localStorage.removeItem('ac_session');
  localStorage.removeItem('ac_household');
}

export const getHousehold = () => localStorage.getItem('ac_household');
export const setHousehold = (id) => localStorage.setItem('ac_household', id);

// ---------- GoTrue ----------

async function authRequest(pathAndQuery, body) {
  let res;
  try {
    res = await fetch(`${BASE}/auth/v1/${pathAndQuery}`, {
      method: 'POST',
      headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error('Sem conexão com o servidor. Verifique sua internet.');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error_description || data.msg || data.message || `Erro ${res.status} no servidor.`);
  }
  return data;
}

export async function signIn(email, password) {
  return saveSession(await authRequest('token?grant_type=password', { email, password }));
}

/** Retorna a sessão, ou null se o projeto exigir confirmação de e-mail. */
export async function signUp(email, password) {
  const data = await authRequest('signup', { email, password });
  return data.access_token ? saveSession(data) : null;
}

/** Garante token válido; refresh se falta <60s. Refresh falhou → limpa sessão e volta ao login. */
async function ensureFresh() {
  const session = getSession();
  if (!session) throw new Error('Sessão expirada. Entre novamente.');
  if (session.expires_at - Date.now() / 1000 > 60) return session;
  try {
    return saveSession(await authRequest('token?grant_type=refresh_token', {
      refresh_token: session.refresh_token,
    }));
  } catch {
    clearSession();
    location.reload();
    throw new Error('Sessão expirada. Entre novamente.');
  }
}

// ---------- PostgREST ----------

export async function rest(method, pathAndQuery, body, prefer) {
  const session = await ensureFresh();
  let res;
  try {
    res = await fetch(`${BASE}/rest/v1/${pathAndQuery}`, {
      method,
      headers: {
        apikey: ANON_KEY,
        Authorization: `Bearer ${session.access_token}`,
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(prefer ? { Prefer: prefer } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Sem conexão com o servidor. Verifique sua internet.');
  }
  if (!res.ok) {
    let message = `Erro ${res.status} no servidor.`;
    try { message = JSON.parse(await res.text()).message || message; } catch { /* corpo não-JSON */ }
    throw new Error(message);
  }
  if (res.status === 204 || (prefer && prefer.includes('return=minimal'))) return null;
  return res.json();
}

export const get = (pathAndQuery) => rest('GET', pathAndQuery);

/** Upsert por uuid: injeta household_id e carimba updated_at (epoch millis) em cada linha. */
export async function upsert(table, rows) {
  const householdId = getHousehold();
  const stamped = rows.map((row) => ({ ...row, household_id: householdId, updated_at: Date.now() }));
  return rest('POST', `${table}?on_conflict=uuid`, stamped, 'resolution=merge-duplicates,return=minimal');
}

/** Delete lógico: tombstone {uuid, deleted:true} — nunca DELETE físico. */
export async function tombstone(table, uuids) {
  if (uuids.length === 0) return;
  await upsert(table, uuids.map((u) => ({ uuid: u, deleted: true })));
}

export const rpc = (name, args) => rest('POST', `rpc/${name}`, args);

/** household_id da membership do usuário logado, ou null (ainda sem família). */
export async function fetchMembership() {
  const rows = await get('household_members?select=household_id');
  return rows.length > 0 ? rows[0].household_id : null;
}

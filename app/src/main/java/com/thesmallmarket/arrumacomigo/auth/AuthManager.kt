package com.thesmallmarket.arrumacomigo.auth

import android.content.Context
import com.thesmallmarket.arrumacomigo.data.local.AppDatabase
import com.thesmallmarket.arrumacomigo.sync.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface AuthState {
    data object SignedOut : AuthState
    data object NeedsHousehold : AuthState
    data class Ready(val householdId: String) : AuthState
}

/**
 * Autenticação Supabase (GoTrue) sem SDK — mesmo estilo HttpURLConnection do SyncEngine.
 * Tokens em SharedPreferences "auth"; estado exposto via StateFlow para o gate na MainActivity.
 */
class AuthManager(context: Context, private val db: AppDatabase) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AuthState> = _state

    val householdId: String? get() = prefs.getString("household_id", null)
    val inviteCode: String? get() = prefs.getString("invite_code", null)

    fun bearerOrNull(): String? = prefs.getString("access_token", null)

    private fun initialState(): AuthState = when {
        prefs.getString("access_token", null) == null -> AuthState.SignedOut
        householdId == null -> AuthState.NeedsHousehold
        else -> AuthState.Ready(householdId!!)
    }

    /** Login com e-mail/senha no Supabase e descobre a família do usuário. */
    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val response = authHttp(
            "token?grant_type=password",
            JSONObject().put("email", email).put("password", password),
        )
        saveSession(response)
        fetchMembership()
    }

    /** Cria a conta e já entra (exige "Confirm email" desabilitado no dashboard do Supabase). */
    suspend fun signUp(email: String, password: String) = withContext(Dispatchers.IO) {
        val response = authHttp(
            "signup",
            JSONObject().put("email", email).put("password", password),
        )
        if (!response.has("access_token")) {
            throw IOException("Conta criada — confirme o e-mail antes de entrar.")
        }
        saveSession(response)
        fetchMembership()
    }

    /** Renova a sessão se estiver a menos de 60s de expirar. GoTrue rotaciona o refresh token. */
    suspend fun refreshIfNeeded() = withContext(Dispatchers.IO) {
        val expiresAt = prefs.getLong("expires_at", 0L)
        val refreshToken = prefs.getString("refresh_token", null) ?: return@withContext
        if (expiresAt - System.currentTimeMillis() / 1000 >= 60) return@withContext
        val response = authHttp(
            "token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken),
        )
        saveSession(response)
    }

    suspend fun createHousehold(name: String) {
        callRpc("create_household", JSONObject().put("p_name", name), "Não foi possível criar a família.")
    }

    suspend fun joinHousehold(code: String) {
        callRpc("join_household", JSONObject().put("p_code", code), "Código de convite inválido.")
    }

    /** Sai da conta: limpa tokens, cursores de pull e o banco local (dados são da família). */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        val editor = syncPrefs.edit()
        syncPrefs.all.keys.filter { it.startsWith("last_pull_") }.forEach { editor.remove(it) }
        editor.apply()
        db.clearAllTables()
        _state.value = AuthState.SignedOut
    }

    // ---------- internals ----------

    private suspend fun fetchMembership() = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            restGet("household_members?select=household_id")
        )
        if (rows.length() > 0) {
            setHousehold(rows.getJSONObject(0).getString("household_id"), inviteCode = null)
        } else {
            _state.value = AuthState.NeedsHousehold
        }
    }

    private suspend fun callRpc(fn: String, body: JSONObject, friendlyError: String) =
        withContext(Dispatchers.IO) {
            refreshIfNeeded()
            val response = try {
                restPost("rpc/$fn", body)
            } catch (e: IOException) {
                throw IOException(friendlyError, e)
            }
            val json = JSONObject(response)
            setHousehold(json.getString("id"), json.optStringOrNull("invite_code"))
        }

    private fun setHousehold(id: String, inviteCode: String?) {
        prefs.edit().apply {
            putString("household_id", id)
            if (inviteCode != null) putString("invite_code", inviteCode)
        }.apply()
        _state.value = AuthState.Ready(id)
    }

    private fun saveSession(response: JSONObject) {
        prefs.edit()
            .putString("access_token", response.getString("access_token"))
            .putString("refresh_token", response.getString("refresh_token"))
            .putLong(
                "expires_at",
                System.currentTimeMillis() / 1000 + response.getLong("expires_in"),
            )
            .putString("user_id", response.getJSONObject("user").getString("id"))
            .apply()
    }

    // ---------- HTTP ----------

    private fun authHttp(pathAndQuery: String, body: JSONObject): JSONObject {
        val text = http("POST", "${SyncConfig.SUPABASE_URL}/auth/v1/$pathAndQuery", body.toString(), bearer = null)
        return JSONObject(text)
    }

    private fun restGet(pathAndQuery: String): String =
        http("GET", "${SyncConfig.SUPABASE_URL}/rest/v1/$pathAndQuery", null, bearer = bearerOrNull())

    private fun restPost(pathAndQuery: String, body: JSONObject): String =
        http("POST", "${SyncConfig.SUPABASE_URL}/rest/v1/$pathAndQuery", body.toString(), bearer = bearerOrNull())

    private fun http(method: String, url: String, body: String?, bearer: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", SyncConfig.SUPABASE_ANON_KEY)
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("Supabase HTTP $code em $method $url: $error")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)
}

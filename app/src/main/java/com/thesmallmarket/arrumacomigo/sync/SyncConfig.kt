package com.thesmallmarket.arrumacomigo.sync

/**
 * Credenciais do projeto Supabase do casal.
 * Enquanto não preenchidas, o sync fica desligado e o app funciona 100% local.
 */
// ponytail: chave anon em constante; mover pra local.properties/BuildConfig se o repo virar público.
object SyncConfig {
    const val SUPABASE_URL = "https://azzhlkxumbyrgdqtoiqb.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6emhsa3h1bWJ5cmdkcXRvaXFiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM0NDQzMDQsImV4cCI6MjA5OTAyMDMwNH0.q0QKNkiv176wC7Z2U1aX7qD5B6WsjRAarzgzV2o1aqg"

    val isConfigured: Boolean
        get() = SUPABASE_URL.startsWith("https://") && SUPABASE_ANON_KEY.isNotBlank()
}

# CLAUDE.md — Arruma Comigo

Guia para o Claude Code ao trabalhar neste repositório.

## O que é o app
**Arruma Comigo** — app Android **nativo para tablet** de gerenciamento de tarefas domésticas
(família de apps "Comigo": Conta-Comigo, Nutre-Comigo). Tarefas por pessoa, cômodos com
tarefas padrão por tipo, recorrência e lembretes por notificação. **100% local** (sem backend/login).

## Stack
- **Kotlin + Jetpack Compose** (Material 3), `minSdk = 26`, `targetSdk = 36`.
- **Room** (persistência local) com **KSP**. DAOs expõem `Flow`.
- **DI manual** via `AppContainer` em `HouseholdApplication` (sem Hilt) — padrão dos codelabs Google.
- **ViewModel + StateFlow**, `collectAsStateWithLifecycle`, fábrica em `ui/AppViewModelProvider.kt`.
- **Navigation Compose** + `WindowSizeClass` adaptativo (NavigationRail em telas largas, bottom bar em estreitas).
- **WorkManager** para lembretes (`notification/ReminderScheduler` + `ReminderWorker` + `BootReceiver`).
- Catálogo de versões em `gradle/libs.versions.toml`.

## Comandos
```bash
./gradlew :app:assembleDebug        # build do APK debug
./gradlew :app:testDebugUnitTest    # testes unitários (RecurrenceCalculator, HouseSeeder)
./gradlew :app:testDebugUnitTest --tests "*RecurrenceCalculatorTest"   # um teste só
./gradlew :app:installDebug         # instala em device/emulador
```
Build/execução é feita preferencialmente no **Android Studio** (emulador de tablet).

## Particularidades de build (AGP 9.1.1) — importante
- AGP 9 tem **Kotlin embutido**: NÃO aplicar `org.jetbrains.kotlin.android` (conflita: "extension 'kotlin' already registered"). Usar só `android.application` + `kotlin.compose` + `ksp`.
- KSP usa `kotlin.sourceSets`, bloqueado pelo Kotlin embutido → `gradle.properties` tem `android.disallowKotlinSourceSets=false`.
- `navigation-compose` **não** é gerenciado pelo Compose BOM → precisa de versão explícita no catálogo.
- `FontVariation` (fontes variáveis) exige `@OptIn(ExperimentalTextApi)`; `FlowRow` usa `@OptIn(ExperimentalLayoutApi)`.

## Design system (ui/theme)
- Paleta **roxo/lavanda**, **neumorfismo** (`Modifier.neumorphic` em `Neumorphism.kt`, sombra dupla via `Paint.setShadowLayer`) + toques skeuomórficos.
- Fontes **Google empacotadas** (`res/font/fredoka.ttf`, `nunito.ttf`, variáveis) — `Type.kt`. Títulos = Fredoka, corpo = Nunito.
- Componentes reutilizáveis em `ui/components/` (`NeoCard`, `NeoButton`, `NeoCheckbox`, `NeoTextField`, `PersonAvatar`, `TaskCard`).

## Estrutura
```
data/        entity (Person, RoomEntity, Task, TaskCompletion, enums), local (DAOs, AppDatabase, Converters),
             repository (HouseholdRepository + OfflineHouseholdRepository), template (RoomTemplates),
             seed (HouseSeeder), RecurrenceCalculator
di/          AppContainer
notification/ NotificationHelper, ReminderScheduler, ReminderWorker, BootReceiver
ui/          theme, components, navigation, today (calendário semanal com abas por dia), rooms, tasks,
             people, history (+ ViewModels), UiModels.kt, DateLabels.kt, DateTicker.kt
```

## Notas
- `RoomEntity` (cômodo) é nomeado assim para não colidir com `androidx.room.Room`.
- Datas/horas (`java.time`) são salvas como strings ISO (ordenáveis) via `Converters`.
- Tarefa recorrente: ao concluir, `completeTask` grava `TaskCompletion` e avança `nextDueDate` (ou arquiva se não-recorrente).
- **Seed inicial**: `HouseSeeder.seedIfEmpty` popula o app na primeira abertura (banco vazio) com a rotina da casa descrita em `PLANO_CASA.md` (documento de domínio na raiz — fonte da escala Giordano/Amanda).
- **Migração destrutiva**: `AppDatabase` (versão 2) usa `fallbackToDestructiveMigration(dropAllTables = true)` — mudar schema exige bump de versão e **apaga todos os dados** (o HouseSeeder reseeda em seguida).
- Fora de escopo (futuro): rodízio automático, gamificação, sync multi-dispositivo (Supabase), lista de compras.

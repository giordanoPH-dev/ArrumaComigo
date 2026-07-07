package com.thesmallmarket.arrumacomigo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thesmallmarket.arrumacomigo.data.entity.PendingDelete
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion

@Database(
    entities = [Person::class, RoomEntity::class, Task::class, TaskCompletion::class, PendingDelete::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun roomDao(): RoomDao
    abstract fun taskDao(): TaskDao
    abstract fun taskCompletionDao(): TaskCompletionDao
    abstract fun pendingDeleteDao(): PendingDeleteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 → v3: colunas de sync (uuid/updatedAt/pendingSync) + fila de deletes.
         * pendingSync DEFAULT 1 marca todos os dados pré-existentes como "dirty",
         * então o primeiro push sobe o banco inteiro para o Supabase.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (t in listOf("people", "rooms", "tasks", "task_completions")) {
                    db.execSQL("ALTER TABLE $t ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $t ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $t ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("UPDATE $t SET uuid = lower(hex(randomblob(16))), updatedAt = strftime('%s','now') * 1000")
                    db.execSQL("CREATE UNIQUE INDEX index_${t}_uuid ON $t (uuid)")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletes (uuid TEXT NOT NULL PRIMARY KEY, tableName TEXT NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arruma_comigo.db",
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

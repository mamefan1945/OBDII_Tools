package com.obdiitools.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, SessionDataPoint::class],
    version = 5,
    exportSchema = true,
)
abstract class OBDDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN maxCoolantTempC INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN make TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sessions ADD COLUMN model TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN totalFuelLitres REAL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN longitude REAL")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN accuracy REAL")
            }
        }
    }
}

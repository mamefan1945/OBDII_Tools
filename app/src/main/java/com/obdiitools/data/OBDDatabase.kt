package com.obdiitools.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, SessionDataPoint::class],
    version = 10,
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtSpeedKph INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtEngineRpm INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtSlipRpm INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtTempC INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtRatio REAL")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtPrimaryPressureMpa REAL")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtSecondaryPressureMpa REAL")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN cvtTargetSecondaryPressureMpa REAL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaFrontLeftWheelSpeedKph INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaFrontRightWheelSpeedKph INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaRearLeftWheelSpeedKph INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaRearRightWheelSpeedKph INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN ambientTempC INTEGER")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN diagnosticEventType TEXT")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN diagnosticEventDetail TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaSelectedGear TEXT")
                database.execSQL("ALTER TABLE session_data_points ADD COLUMN mazdaTransmissionTempC REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN vin TEXT")
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

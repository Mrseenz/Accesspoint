package com.example.hotspotloginapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

object UserLogContract {
    object LogEntry : BaseColumns {
        const val TABLE_NAME = "mock_logins"
        const val COLUMN_NAME_TIMESTAMP = "timestamp"
        const val COLUMN_NAME_DEVICE_IDENTIFIER = "device_identifier" // e.g., IP Address
        const val COLUMN_NAME_MOCK_SERVICE = "mock_service" // Gmail, Outlook, iCloud
        const val COLUMN_NAME_TYPED_EMAIL = "typed_email"
        const val COLUMN_NAME_TYPED_PASSWORD = "typed_password"
    }
}

class UserLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "UserActivity.db"

        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE ${UserLogContract.LogEntry.TABLE_NAME} (" +
            "${BaseColumns._ID} INTEGER PRIMARY KEY," +
            "${UserLogContract.LogEntry.COLUMN_NAME_TIMESTAMP} TEXT," +
            "${UserLogContract.LogEntry.COLUMN_NAME_DEVICE_IDENTIFIER} TEXT," +
            "${UserLogContract.LogEntry.COLUMN_NAME_MOCK_SERVICE} TEXT," +
            "${UserLogContract.LogEntry.COLUMN_NAME_TYPED_EMAIL} TEXT," +
            "${UserLogContract.LogEntry.COLUMN_NAME_TYPED_PASSWORD} TEXT)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${UserLogContract.LogEntry.TABLE_NAME}"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for mock login data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
}

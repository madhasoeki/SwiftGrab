package com.example.swiftgrab.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.swiftgrab.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AppDatabaseMigrationTest {

    @Test
    fun migration1To2_hasExpectedVersions() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration2To3_hasExpectedVersionsAndCreatesIndices() {
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)

        val executedSql = mutableListOf<String>()
        val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        executedSql.add(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        AppDatabase.MIGRATION_2_3.migrate(dummyDb)

        assertEquals(2, executedSql.size)
        assertTrue(executedSql.any { it.contains("index_download_tasks_status") && it.contains("download_tasks") })
        assertTrue(executedSql.any { it.contains("index_download_tasks_createdAt") && it.contains("download_tasks") })
    }
}

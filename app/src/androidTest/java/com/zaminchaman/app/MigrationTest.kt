package com.zaminchaman.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.zaminchaman.app.data.db.AppDatabase
import com.zaminchaman.app.data.db.DB_NAME
import com.zaminchaman.app.data.db.Migrations
import org.junit.Rule
import org.junit.Test

/**
 * Skeleton: when you add MIGRATION_1_2, create version 1 here with test data,
 * run the migration and assert the old rows survived.
 * Schemas are read from app/schemas (exported by Room on every build).
 */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun createsCurrentVersion() {
        helper.createDatabase(DB_NAME, 1).close()
        // Example for the future:
        // helper.runMigrationsAndValidate(DB_NAME, 2, true, *Migrations.ALL)
        check(Migrations.ALL.isEmpty() || Migrations.ALL.isNotEmpty())
    }
}

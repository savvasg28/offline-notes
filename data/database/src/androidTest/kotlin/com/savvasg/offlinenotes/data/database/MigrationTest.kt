package com.savvasg.offlinenotes.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = OfflineNotesDatabase::class,
    )

    @Test
    fun migrate1To2_keepsExistingOpsAndAddsNullableRejectionColumns() {
        val before = helper.createDatabase(version = 1)
        before.execSQL(
            "INSERT INTO notes (id, title, body, serverVersion, syncState, isDeleted, createdAt, lastUpdated) " +
                "VALUES ('note-1', 'T', 'B', 'v1', 'PENDING', 0, 1000, 1000)",
        )
        before.execSQL(
            "INSERT INTO outbox (opId, noteId, opType, title, body, status, createdAt, attemptCount) " +
                "VALUES ('op-1', 'note-1', 'UPDATE', 'T', 'B', 'PENDING', 1000, 0)",
        )
        before.close()

        val after = helper.runMigrationsAndValidate(
            version = 2,
            migrations = listOf(OfflineNotesDatabase.MIGRATION_1_2),
        )
        val statement = after.prepare("SELECT opId, status, rejectionCode, rejectionMessage FROM outbox")

        assertThat(statement.step()).isTrue()
        assertThat(statement.getText(0)).isEqualTo("op-1")
        assertThat(statement.getText(1)).isEqualTo("PENDING")
        assertThat(statement.isNull(2)).isTrue()
        assertThat(statement.isNull(3)).isTrue()

        statement.close()
        after.close()
    }
}

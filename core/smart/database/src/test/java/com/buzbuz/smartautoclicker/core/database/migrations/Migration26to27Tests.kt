// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.ContentValues
import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.database.ACTION_TABLE
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.EVENT_TABLE
import com.buzbuz.smartautoclicker.core.database.SCENARIO_TABLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration26to27Tests {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test fun existingActionsAndEventsKeepEmptyGroupsAndDefaultOperator() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-26-27").path
        helper.createDatabase(path, 26).use { db ->
            db.insert(SCENARIO_TABLE, 0, ContentValues().apply {
                put("id", 1L); put("name", "Scene"); put("detection_quality", 1)
                put("compute_rate", 0.0); put("randomize", 0); put("keep_screen_on", 0)
            })
            db.insert(EVENT_TABLE, 0, ContentValues().apply {
                put("id", 1L); put("scenario_id", 1L); put("name", "Flow")
                put("operator", 1); put("priority", 0); put("enabled_on_start", 1); put("type", "IMAGE_EVENT")
            })
            db.insert(ACTION_TABLE, 0, ContentValues().apply {
                put("id", 1L); put("eventId", 1L); put("priority", 0)
                put("name", "Click"); put("type", "CLICK")
            })
        }
        helper.runMigrationsAndValidate(path, 27, true).use { db ->
            db.query("SELECT action_groups FROM $EVENT_TABLE WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("", it.getString(0))
            }
            db.query("SELECT additional_execution_condition_ids, execution_condition_operator FROM $ACTION_TABLE WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("", it.getString(0)); assertEquals(1, it.getInt(1))
            }
        }
    }
}

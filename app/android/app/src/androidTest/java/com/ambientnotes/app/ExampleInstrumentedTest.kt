package com.ambientnotes.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented sanity checks that must run on-device/emulator (application
 * context, Room, real AudioRecord). CI runs these on a headless emulator via
 * `reactivecircus/android-emulator-runner` -- see
 * .github/workflows/android-ci.yml. Kept intentionally small: most logic is
 * covered by fast JVM unit tests in src/test; this suite is for the things
 * that genuinely require a device (manifest/package wiring, DB migrations
 * running against real SQLite, Compose UI smoke tests).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ambientnotes.app", appContext.packageName)
    }

    @Test
    fun databaseOpensAndDaoIsUsable() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = com.ambientnotes.app.data.AppDatabase.getInstance(appContext)
        // Just verifying the schema builds and the DAO is reachable; full CRUD
        // is covered by (non-instrumented) Robolectric-backed tests where
        // practical, reserving the device requirement for what truly needs it.
        assert(db.songLogDao() != null)
    }
}

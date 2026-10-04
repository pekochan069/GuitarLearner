package com.pekochan069.guitarlearner

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.rules.ExternalResource

class NotificationActivityRule(
    private val initialAction: String = Intent.ACTION_MAIN,
) : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    lateinit var activity: MainActivity
        private set

    override fun before() {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .setAction(initialAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (initialAction == Intent.ACTION_MAIN) intent.addCategory(Intent.CATEGORY_LAUNCHER)
        activity = instrumentation.startActivitySync(intent) as MainActivity
        awaitResumed()
    }

    fun recreate() {
        val original = activity
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync { original.recreate() }
            activity = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 5_000)) {
                "MainActivity was not recreated"
            } as MainActivity
            assertNotSame(original, activity)
            awaitResumed()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    override fun after() {
        if (::activity.isInitialized) {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun awaitResumed() {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(Stage.RESUMED, ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity))
        }
    }
}

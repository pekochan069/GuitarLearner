package com.pekochan069.guitarlearner

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetronomeLaunchInputTest {
    @Test
    fun consumedColdNotificationIsSanitizedAndCannotReplayAfterRecreation(): Unit {
        val intent = notificationIntent()
        val input = MetronomeLaunchInput()
        val events = mutableListOf<FoundationEvent>()
        input.restore(null, intent)
        assertTrue(input.pending)
        assertNull(intent.action)
        input.dispatch(events::add)
        val saved = Bundle()
        input.save(saved)
        assertTrue(saved.isEmpty)
        val recreated = MetronomeLaunchInput()
        recreated.restore(saved, intent)
        recreated.dispatch(events::add)
        assertFalse(recreated.pending)
        assertEquals(listOf(FoundationEvent.OpenFeature(FeatureId.Metronome)), events)
    }

    @Test
    fun undeliveredNotificationSurvivesSavedStateAndDispatchesOnce(): Unit {
        val input = MetronomeLaunchInput()
        input.restore(null, notificationIntent())
        val saved = Bundle()
        input.save(saved)
        assertFalse(saved.isEmpty)
        val restored = MetronomeLaunchInput()
        restored.restore(saved, notificationIntent())
        val events = mutableListOf<FoundationEvent>()
        restored.dispatch(events::add)
        restored.dispatch(events::add)
        assertFalse(restored.pending)
        assertEquals(listOf(FoundationEvent.OpenFeature(FeatureId.Metronome)), events)
        val consumed = Bundle()
        restored.save(consumed)
        assertTrue(consumed.isEmpty)
    }

    @Test
    fun restoredTaskIgnoresItsOriginalNotificationWithoutAHistoryFlagAndAcceptsANewDelivery(): Unit {
        val restored = MetronomeLaunchInput()
        val original = notificationIntent()
        restored.restore(Bundle(), original)
        assertNull(original.action)
        assertFalse(restored.pending)
        val events = mutableListOf<FoundationEvent>()
        restored.dispatch(events::add)
        assertTrue(events.isEmpty())
        val delivered = notificationIntent()
        restored.receive(delivered)
        assertNull(delivered.action)
        assertTrue(restored.pending)
        restored.dispatch(events::add)
        restored.dispatch(events::add)
        assertFalse(restored.pending)
        assertEquals(listOf(FoundationEvent.OpenFeature(FeatureId.Metronome)), events)
    }

    @Test
    fun unrelatedExternalActionsCannotSelectDevelopmentSamples(): Unit {
        val input = MetronomeLaunchInput()
        input.restore(null, Intent("sample:tuner"))
        val events = mutableListOf<FoundationEvent>()
        input.dispatch(events::add)
        assertFalse(input.pending)
        assertTrue(events.isEmpty())
    }
}

private fun notificationIntent(): Intent = Intent(AndroidMetronomeHost.ACTION_OPEN_METRONOME)

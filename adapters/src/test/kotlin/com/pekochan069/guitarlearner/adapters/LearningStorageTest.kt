package com.pekochan069.guitarlearner.adapters

import android.content.SharedPreferences
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.LearningFailure
import com.pekochan069.guitarlearner.domain.LearningProgress
import com.pekochan069.guitarlearner.domain.LessonId
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LearningStorageTest {
    @Test fun successfulCheckedWriteRestoresStableLessonIdsInANewStore() {
        val preferences = Preferences(null)
        val store = LearningStorage(preferences.value)
        assertEquals(Either.Right(LearningProgress()), store.read())
        val progress = LearningProgress(setOf(LessonId.Scales, LessonId.NotesIntervals), LessonId.Scales)
        assertEquals(Either.Right(Unit), store.write(progress))
        assertEquals("1|scales|notes_intervals,scales", preferences.source)
        assertEquals(Either.Right(progress), LearningStorage(preferences.value).read())
        val everyLesson = LearningProgress(LessonId.entries.toSet(), LessonId.PalmMute)
        assertEquals(everyLesson, decodeLearningProgress(encodeLearningProgress(everyLesson)))
    }

    @Test fun failedCommitRestoresAcceptedPreferencesAndRetryWritesTheLatestProgress() {
        val initial = LearningProgress(setOf(LessonId.NotesIntervals), LessonId.NotesIntervals)
        val preferences = Preferences(encodeLearningProgress(initial))
        val store = LearningStorage(preferences.value)
        assertEquals(Either.Right(initial), store.read())
        preferences.failNextCommit = true
        val requested = LearningProgress(initial.completed + LessonId.Scales, LessonId.Scales)
        assertEquals(Either.Left(LearningFailure.WriteFailed), store.write(requested))
        assertEquals(encodeLearningProgress(initial), preferences.source)
        assertEquals(Either.Right(initial), LearningStorage(preferences.value).read())
        assertEquals(2, preferences.commits)
        assertEquals(Either.Right(Unit), store.write(requested))
        assertEquals(Either.Right(requested), LearningStorage(preferences.value).read())
        val absent = Preferences(null)
        val absentStore = LearningStorage(absent.value)
        assertTrue(absentStore.read().isRight())
        absent.failNextCommit = true
        assertTrue(absentStore.write(requested).isLeft())
        assertNull(absent.source)
    }

    @Test fun unreadableSourcesAreNotOverwrittenAndExplicitReadCanRecover() {
        for (source in listOf("", "2||", "1|missing|", "1||scales,scales", "1|scales|missing", "1||scales|extra", 17)) {
            val preferences = Preferences(source)
            val store = LearningStorage(preferences.value)
            assertEquals(Either.Left(LearningFailure.ReadFailed), store.read())
            assertEquals(Either.Left(LearningFailure.ReadFailed), store.write(LearningProgress()))
            assertEquals(source, preferences.source)
            assertEquals(0, preferences.commits)
            preferences.source = "1|scales|notes_intervals,scales"
            assertEquals(Either.Right(LearningProgress(setOf(LessonId.NotesIntervals, LessonId.Scales), LessonId.Scales)), store.read())
            assertEquals(Either.Right(Unit), store.write(LearningProgress(lastViewed = LessonId.ChordConstruction)))
        }
        val denied = Preferences(null)
        denied.failRead = true
        assertEquals(Either.Left(LearningFailure.ReadFailed), LearningStorage(denied.value).read())
    }

    private class Preferences(var source: Any?) {
        var failNextCommit = false
        var failRead = false
        var commits = 0
        val value: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, arguments ->
            when (method.name) {
                "getString" -> {
                    if (failRead) throw SecurityException()
                    if (source != null && source !is String) throw ClassCastException()
                    source ?: arguments?.get(1)
                }
                "edit" -> editor()
                else -> error("Unexpected preferences call ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            var pending = source
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, arguments ->
                when (method.name) {
                    "putString" -> { pending = arguments?.get(1); proxy }
                    "remove" -> { pending = null; proxy }
                    "commit" -> {
                        source = pending
                        commits++
                        val success = !failNextCommit
                        failNextCommit = false
                        success
                    }
                    else -> error("Unexpected editor call ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}

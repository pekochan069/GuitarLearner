package com.pekochan069.guitarlearner

import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred

internal class ControlledCommitPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
    @Volatile var failNext = false
    @Volatile private var nextGate: CommitGate? = null
    private var activeGate: CommitGate? = null
    fun blockNextCommit(): CommitGate = CommitGate().also { nextGate = it; activeGate = it }
    fun releaseCommit() { activeGate?.open() }

    override fun edit(): SharedPreferences.Editor {
        val editor = delegate.edit()
        return object : SharedPreferences.Editor by editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                editor.putString(key, value)
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
            override fun commit(): Boolean {
                nextGate?.let { gate -> nextGate = null; gate.waitForRelease() }
                val saved = editor.commit()
                return if (failNext) { failNext = false; false } else saved
            }
        }
    }
}

internal class CommitGate {
    val entered = CompletableDeferred<Unit>()
    private val released = CountDownLatch(1)
    fun open() = released.countDown()
    fun waitForRelease() {
        entered.complete(Unit)
        check(released.await(10, TimeUnit.SECONDS)) { "Test did not release the preference commit" }
    }
}

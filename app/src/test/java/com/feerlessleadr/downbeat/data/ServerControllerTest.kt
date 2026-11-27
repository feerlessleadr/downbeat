package com.feerlessleadr.downbeat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class SrvStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class ServerControllerTest {
    private val fake = FakeMaServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: MusicRepository
    private lateinit var controller: ServerController

    @Before fun setUp() {
        fake.start()
        fake.tokens.add("good")
        fake.results["players/all"] = "[]"
        fake.results["player_queues/all"] = "[]"
        fake.results["config/providers"] = "[]"
        repo = MusicRepository(OkHttpClient(), SrvStore(Session(fake.url(), "good")), scope, requireHttps = false, backoffMs = listOf(50))
        controller = ServerController(repo)
    }

    @After fun tearDown() {
        repo.signOut()
        scope.cancel()
        fake.stop()
    }

    private fun online() = runBlocking {
        repo.start()
        withTimeout(10_000) { repo.state.first { it.phase == Phase.Online } }
    }

    private fun args(command: String) = fake.received.last { it.first == command }.second

    @Test fun taskActionsSendTheTaskId() = runBlocking {
        online()
        assertNull(controller.runTask("t1"))
        assertEquals("t1", args("tasks/run")!!["task_id"]!!.jsonPrimitive.content)
        assertNull(controller.cancelTask("t2"))
        assertEquals("t2", args("tasks/cancel")!!["task_id"]!!.jsonPrimitive.content)
        assertNull(controller.retryTask("t3"))
        assertEquals("t3", args("tasks/retry")!!["task_id"]!!.jsonPrimitive.content)
    }

    @Test fun theScheduleSwitchSendsTheTaskAndTheWantedState() = runBlocking {
        online()
        assertNull(controller.setTaskEnabled("t1", false))
        val a = args("tasks/set_enabled")!!
        assertEquals("t1", a["task_id"]!!.jsonPrimitive.content)
        assertFalse(a["enabled"]!!.jsonPrimitive.boolean)
        assertNull(controller.setTaskEnabled("t1", true))
        assertTrue(args("tasks/set_enabled")!!["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test fun clearFinishedAndSyncAllSendNoFilters() = runBlocking {
        online()
        assertNull(controller.clearFinishedTasks())
        assertNull(controller.syncAllLibraries())
        // "No filters" means no arguments at all: every provider is synced.
        assertNull(args("music/sync"))
        assertNull(args("tasks/clear_finished"))
    }

    @Test fun revokingATokenSendsItsId() = runBlocking {
        online()
        assertNull(controller.revokeToken("tok-9"))
        assertEquals("tok-9", args("auth/token/revoke")!!["token_id"]!!.jsonPrimitive.content)
    }

    @Test fun aTaskLogComesBackAsTextOrAMessage() = runBlocking {
        online()
        fake.results["tasks/log"] = "\"line one\\nline two\""
        assertEquals("line one\nline two", (controller.taskLog("t1") as TextResult.Text).text)
        fake.errors["tasks/log"] = "No such task"
        assertEquals("No such task", (controller.taskLog("t1") as TextResult.Failed).message)
    }

    @Test fun refusalsAndOfflineComeBackAsMessages() = runBlocking {
        assertEquals("Not connected to the server.", controller.runTask("t1"))
        assertTrue(controller.taskLog("t1") is TextResult.Failed)
        online()
        fake.errors["auth/token/revoke"] = "Permission denied"
        assertEquals("Permission denied", controller.revokeToken("x"))
    }
}

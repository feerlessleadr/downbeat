package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.AdminUser
import com.feerlessleadr.downbeat.ma.AuthToken
import com.feerlessleadr.downbeat.ma.BackgroundTask
import com.feerlessleadr.downbeat.ma.MaJson
import com.feerlessleadr.downbeat.ma.ServerAbout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ServerAdminTest {
    private val utc = ZoneId.of("UTC")

    @Test fun datesCanBeSecondsMillisecondsOrIsoText() {
        // 2026-11-27 18:21:00 UTC
        val seconds = 1_795_803_660L
        assertEquals("Nov 27, 2026, 6:21 PM", formatWhen(JsonPrimitive(seconds), utc))
        assertEquals("Nov 27, 2026, 6:21 PM", formatWhen(JsonPrimitive(seconds * 1000), utc))
        assertEquals("Nov 27, 2026, 6:21 PM", formatWhen(JsonPrimitive(seconds.toDouble()), utc))
        assertEquals("Nov 27, 2026, 6:21 PM", formatWhen(JsonPrimitive("2026-11-27T18:21:00Z"), utc))
        assertEquals("Nov 27, 2026, 6:21 PM", formatWhen(JsonPrimitive("2026-11-27T18:21:00"), utc))
    }

    @Test fun missingOrUnreadableDatesSayNeverOrShowTheText() {
        assertEquals("never", formatWhen(null, utc))
        assertEquals("never", formatWhen(JsonNull, utc))
        assertEquals("never", formatWhen(JsonPrimitive(""), utc))
        assertEquals("sometime soon", formatWhen(JsonPrimitive("sometime soon"), utc))
    }

    @Test fun tokensAreNamedByKindAndThisPhoneIsRecognisedByItsName() {
        val long = AuthToken(tokenId = "1", name = "Downbeat a1b2c3", isLongLived = true)
        val session = AuthToken(tokenId = "2", name = "Web (Chrome)", isLongLived = false)
        assertEquals("Long-lived", tokenKind(long))
        assertEquals("Sign-in session", tokenKind(session))
        assertTrue(isThisPhone(long, "Downbeat a1b2c3"))
        assertFalse(isThisPhone(long, "Downbeat zzzzzz"))
        assertFalse(isThisPhone(long, null))
    }

    @Test fun aLongLogIsCutToItsLastLines() {
        val log = (1..500).joinToString("\n") { "line $it" }
        val tail = logTail(log, 200)
        assertEquals(200, tail.lines().size)
        assertEquals("line 301", tail.lines().first())
        assertEquals("line 500", tail.lines().last())
        assertEquals("a\nb", logTail("a\nb\n", 200))
        assertEquals("", logTail("", 200))
    }

    @Test fun aTaskLineShowsStatusProgressAndTimes() {
        val task = BackgroundTask(
            id = "t1", name = "Sync", status = "running", progress = 0.42, progressText = "Albums",
            lastRun = JsonPrimitive("2026-11-27T10:00:00Z"), nextRun = JsonNull,
        )
        assertEquals("Running · 42 percent · Albums · last run Nov 27, 2026, 10:00 AM", taskSummary(task, utc))
        assertEquals("Finished", taskSummary(BackgroundTask(status = "finished"), utc))
    }

    @Test fun aTaskErrorCanBeTextOrAnObject() {
        assertNull(taskError(BackgroundTask()))
        assertNull(taskError(BackgroundTask(lastError = JsonNull)))
        assertEquals("boom", taskError(BackgroundTask(lastError = JsonPrimitive("boom"))))
        val obj = MaJson.parseToJsonElement("""{"message":"Could not reach the server","code":5}""")
        assertEquals("Could not reach the server", taskError(BackgroundTask(lastError = obj)))
    }

    @Test fun theServersShapesDecode() {
        val about = MaJson.decodeFromString(
            ServerAbout.serializer(),
            """{"server_id":"x","server_version":"2.10.13","schema_version":65,"name":"Music Assistant","status":"running",
               "homeassistant_addon":true,"has_remote_access":false,"base_url":"http://private","internal_url":"http://private2"}""",
        )
        assertEquals("2.10.13", about.serverVersion)
        assertTrue(about.homeAssistantAddon)
        val users = MaJson.decodeFromString(
            ListSerializer(AdminUser.serializer()),
            """[{"user_id":"u1","username":"kev","role":"admin","enabled":true,"display_name":"Kevin","created_at":"2026-11-25T11:09:00Z",
                "preferences":{},"provider_filter":[],"player_filter":[]}]""",
        )
        assertEquals("admin", users.single().role)
        val tokens = MaJson.decodeFromString(
            ListSerializer(AuthToken.serializer()),
            """[{"token_id":"t1","user_id":"u1","token_hash":"SECRET","name":"Downbeat","created_at":1795803660.5,"expires_at":null,
                "last_used_at":"2026-11-27T10:00:00Z","is_long_lived":true}]""",
        )
        assertTrue(tokens.single().isLongLived)
        val tasks = MaJson.decodeFromString(
            ListSerializer(BackgroundTask.serializer()),
            """[{"id":"t1","name":"Library sync","status":"pending","progress":null,"last_run":null,"next_run":1795807260,"logs":"x","metadata":{},"allow_retry":true}]""",
        )
        assertTrue(tasks.single().allowRetry)
    }
}

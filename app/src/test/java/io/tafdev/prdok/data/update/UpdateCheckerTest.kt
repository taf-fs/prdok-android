package io.tafdev.prdok.data.update

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeUpdateStore : UpdateStore {
    val current = MutableStateFlow(UpdateState())
    override val state: Flow<UpdateState> = current

    override suspend fun saveCheck(at: Instant, latest: AppRelease?) {
        current.update { it.copy(lastCheckedAt = at, latest = latest) }
    }

    override suspend fun dismiss(version: String) {
        current.update { it.copy(dismissedVersion = version) }
    }
}

private fun releaseJson(tag: String) =
    """{"tag_name": "$tag", "html_url": "https://page/$tag", "assets": []}"""

class UpdateCheckerTest {

    private lateinit var server: MockWebServer
    private val store = FakeUpdateStore()
    private var now = Instant.parse("2026-09-26T08:00:00Z")

    /** The installed app is 1.0.0. */
    private fun checker() = UpdateChecker(
        OkHttpClient(),
        store,
        installedVersionCode = 10_000,
        latestReleaseUrl = server.url("/releases/latest").toString(),
        clock = { now },
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a newer release is offered`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        val checker = checker()

        checker.checkIfDue()

        assertEquals("1.1.0", checker.prompt.first()?.version?.name)
        assertEquals("1.1.0", checker.available.first()?.version?.name)
        assertEquals("application/vnd.github+json", server.takeRequest().getHeader("Accept"))
    }

    @Test
    fun `the installed version itself is not offered`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.0.0")))
        val checker = checker()

        checker.checkIfDue()

        assertNull(checker.prompt.first())
        assertNull(checker.available.first())
    }

    @Test
    fun `later hides the prompt for that version but About still has it`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        val checker = checker()
        checker.checkIfDue()

        checker.dismiss(checker.prompt.first()!!)

        assertNull(checker.prompt.first())
        assertEquals("1.1.0", checker.available.first()?.version?.name)
    }

    @Test
    fun `a version after the dismissed one is offered again`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        server.enqueue(MockResponse().setBody(releaseJson("v1.2.0")))
        val checker = checker()
        checker.checkIfDue()
        checker.dismiss(checker.prompt.first()!!)

        now += UpdateChecker.CHECK_INTERVAL
        checker.checkIfDue()

        assertEquals("1.2.0", checker.prompt.first()?.version?.name)
    }

    @Test
    fun `a recent check skips the network`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        val checker = checker()
        checker.checkIfDue()

        now += UpdateChecker.CHECK_INTERVAL - Duration.ofMinutes(1)
        checker.checkIfDue()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a clock moved back doesn't block checks`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        val checker = checker()
        checker.checkIfDue()

        now -= Duration.ofDays(1)
        checker.checkIfDue()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed check changes nothing and is retried next time`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setBody("<html>"))
        server.enqueue(MockResponse().setBody(releaseJson("v1.1.0")))
        val checker = checker()

        checker.checkIfDue()
        checker.checkIfDue()
        assertEquals(UpdateState(), store.current.value)

        checker.checkIfDue()
        assertEquals(3, server.requestCount)
        assertEquals("1.1.0", checker.prompt.first()?.version?.name)
    }

    @Test
    fun `a repo without releases is a successful check with nothing to offer`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val checker = checker()

        checker.checkIfDue()

        assertEquals(now, store.current.value.lastCheckedAt)
        assertNull(checker.prompt.first())
    }
}

package io.tafdev.prdok.data.update

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The public GitHub API for this repo's newest release; no token needed while the repo is public. */
const val LATEST_RELEASE_URL = "https://api.github.com/repos/taf-fs/prdok-android/releases/latest"

/**
 * Asks GitHub whether a newer release than the installed one is out.
 *
 * GitHub allows 60 unauthenticated API calls an hour per IP address, and everyone on the same
 * Wi-Fi shares one, so a check runs at most once per [CHECK_INTERVAL]. Whatever it finds is
 * stored, so [available] and [prompt] work offline and survive restarts.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val store: UpdateStore,
    private val installedVersionCode: Int,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    private val clock: () -> Instant = Instant::now,
) {
    /** The newest release, when it's newer than the installed app. */
    val available: Flow<AppRelease?> = store.state.map { it.newerRelease() }

    /** [available], unless the user already put that version off with "Later". */
    val prompt: Flow<AppRelease?> = store.state.map { state ->
        state.newerRelease()?.takeIf { it.version.name != state.dismissedVersion }
    }

    /**
     * Checks GitHub if the last successful check is older than [CHECK_INTERVAL]. Failures are
     * silent and not recorded, so the next launch simply tries again.
     */
    suspend fun checkIfDue() {
        val now = clock()
        val last = store.state.first().lastCheckedAt
        // A last check "in the future" means the phone's clock was moved back; check anyway.
        if (last != null && !now.isBefore(last) && Duration.between(last, now) < CHECK_INTERVAL) return

        val latest = try {
            fetchLatest()
        } catch (_: IOException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        store.saveCheck(now, latest)
    }

    suspend fun dismiss(release: AppRelease) = store.dismiss(release.version.name)

    private suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            when {
                // The repo has no published release yet.
                response.code == 404 -> null
                // 403/429 is the rate limit; treat it like any other failure and retry later.
                !response.isSuccessful -> throw IOException("GitHub answered HTTP ${response.code}")
                else -> ReleaseParser.parse(response.body?.string().orEmpty())
            }
        }
    }

    private fun UpdateState.newerRelease(): AppRelease? =
        latest?.takeIf { it.version.code > installedVersionCode }

    companion object {
        val CHECK_INTERVAL: Duration = Duration.ofHours(6)
    }
}

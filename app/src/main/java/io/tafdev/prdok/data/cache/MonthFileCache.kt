package io.tafdev.prdok.data.cache

import java.io.File
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One cached month: when it was fetched plus the payload. */
data class CacheEntry<T>(val fetchedAt: Instant, val value: T)

/** Converts a payload to/from JSON. Decoding should throw on anything it can't read. */
interface MonthCacheCodec<T> {
    fun encode(value: T): JsonElement
    fun decode(element: JsonElement): T
}

/**
 * Stores one JSON file per month in [dir] (`<prefix>-yyyy-MM.json`), each holding
 * `fetchedAt` and the encoded payload. Staleness is [CachedMonthSource]'s job.
 *
 * Missing, corrupt, or unreadable file simply means "no entry",
 * so a bad cache can only cost a refetch, never throw.
 */
class MonthFileCache<T>(
    private val dir: File,
    private val prefix: String,
    private val codec: MonthCacheCodec<T>,
) {

    suspend fun read(month: YearMonth): CacheEntry<T>? = withContext(Dispatchers.IO) {
        val file = fileFor(month)
        if (!file.exists()) return@withContext null
        runCatching {
            val root = Json.parseToJsonElement(file.readText()) as JsonObject
            val fetchedAt = Instant.ofEpochMilli((root["fetchedAt"] as JsonPrimitive).content.toLong())
            val value = codec.decode(root["data"] ?: error("missing data"))
            CacheEntry(fetchedAt, value)
        }.getOrNull()
    }

    /** Writes atomically (temp file + rename) so a crash mid-write can't leave a torn file. */
    suspend fun write(month: YearMonth, entry: CacheEntry<T>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val json = buildJsonObject {
            put("fetchedAt", entry.fetchedAt.toEpochMilli())
            put("data", codec.encode(entry.value))
        }
        val target = fileFor(month)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not replace ${target.name}" }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.name.startsWith("$prefix-") }
            ?.forEach { it.delete() }
        Unit
    }

    private fun fileFor(month: YearMonth) = File(dir, "$prefix-${month.year}-${"%02d".format(month.monthValue)}.json")
}

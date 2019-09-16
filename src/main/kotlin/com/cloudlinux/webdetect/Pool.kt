package com.cloudlinux.webdetect

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet

typealias Checksum = ChecksumLong

/**
 * Prefix 'F' as it's fastutil library
 */
typealias FMutableSet<T> = ObjectOpenHashSet<T>

typealias IntMutableSet = IntOpenHashSet

typealias FMutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias FMutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias FSortedMap<K, V> = Object2ObjectRBTreeMap<K, V>
typealias FSortedSet<T> = ObjectRBTreeSet<T>

typealias FArrayList<T> = ObjectArrayList<T>

/**
 * Holds information about checksums and app-versions parsed from given DB.
 * Perform deduplication of [Checksum], [String], [AppVersion] to keep heap as small as possible.
 */
class WebdetectContext(
    val pathToCsv: String
) {
    val checksumToAppVersions: FMutableMap<Checksum, FMutableSet<AppVersion>> = FMutableMap(7_000_000)
    val appVersionsToChecksums: FMutableMap<AppVersion, FMutableSet<Checksum>> = FMutableMap(500_000)
    val pool = Pool()

    data class PathDepth(
        val path: String?,
        val depth: Int?
    )

    fun doPooling(
        app: String,
        version: String,
        checksum: String,
        depth: Int? = null,
        path: String? = null
    ) {
        val av = appVersion(app, version)
        val cs = checksum(checksum)

        appVersionsToChecksums.computeIfAbsent(av) { FMutableSet() } += cs
        checksumToAppVersions.computeIfAbsent(cs) { FMutableSet() } += av
    }

    fun cleanup() {
        checksumToAppVersions.clear()
        checksumToAppVersions.trim()
        appVersionsToChecksums.clear()
        appVersionsToChecksums.trim()
    }

    private fun checksum(cs: String): Checksum =
        pool.checksums.computeIfAbsent(string(cs)) { key -> ChecksumLong(key) }

    private fun string(string: String): String = pool.strings.computeIfAbsent(string) { string }

    private fun appVersion(app: String, version: String): AppVersion =
        pool.appVersions.computeIfAbsent(app + version) {
            AppVersion.Single(string(app), string(version), string(app + version))
        }

    /**
     * Separated from [WebdetectContext], as it can be cleaned-up a little bit earlier
     */
    class Pool {
        val appVersions = FMutableMap<String, AppVersion>(500_000)
        val checksums = FMutableMap<String, Checksum>(7_000_000)
        val strings = FMutableMap<String, String>(8_500_000)

        fun cleanup() {
            appVersions.clear()
            appVersions.trim()
            checksums.clear()
            checksums.trim()
            strings.clear()
            strings.trim()
        }
    }
}

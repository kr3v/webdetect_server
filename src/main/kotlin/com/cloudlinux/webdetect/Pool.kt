package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.ChecksumKey
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet

typealias Checksum = ChecksumLong

/**
 * Prefix 'F' as it's fastutil library
 */
typealias FMutableSet<T> = ObjectOpenHashSet<T>

typealias FMutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias FMutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias FSortedMap<K, V> = Object2ObjectRBTreeMap<K, V>
typealias FSortedSet<T> = ObjectRBTreeSet<T>


interface EntityFactory<K, I, E> {
    fun constructor(values: List<I>, cached: I.() -> I): E
    fun key(values: List<I>): K
    fun isValid(values: List<I>): Boolean
}

/**
 * Holds information about checksums and app-versions parsed from given DB.
 * Perform deduplication of [Checksum], [String], [AppVersion] to keep heap as small as possible.
 */
class WebdetectContext<C : ChecksumKey<C>>(
    private val appVersionFactory: EntityFactory<String, String, AppVersion>,
    private val checksumFactory: EntityFactory<String, String, C>
) {
    val checksumToAppVersions: FMutableMap<C, FMutableSet<AppVersion>> = FMutableMap()
    val appVersionsToChecksums: FMutableMap<AppVersion, FMutableSet<C>> = FMutableMap()
    val pool = Pool<C>()

    fun doPooling(
        avValues: List<String>,
        cValues: List<String>
    ) {
        val av = appVersion(avValues)
        val cs = checksum(cValues)
        appVersionsToChecksums.computeIfAbsent(av) { FMutableSet() } += cs
        checksumToAppVersions.computeIfAbsent(cs) { FMutableSet() } += av
    }

    fun cleanup() {
        pool.cleanup()
        checksumToAppVersions.clear()
        checksumToAppVersions.trim()
        appVersionsToChecksums.clear()
        appVersionsToChecksums.trim()
    }

    private fun checksum(values: List<String>): C =
        pool.checksums.computeIfAbsent(string(checksumFactory.key(values))) {
            checksumFactory.constructor(
                values,
                ::string
            )
        }

    private fun appVersion(values: List<String>): AppVersion =
        pool.appVersions.computeIfAbsent(string(appVersionFactory.key(values))) {
            appVersionFactory.constructor(
                values,
                ::string
            )
        }

    private fun string(string: String): String =
        pool.strings.computeIfAbsent(string) { string }

    /**
     * Separated from [WebdetectContext], as it can be cleaned-up a little bit earlier
     */
    class Pool<C : ChecksumKey<C>> {
        val appVersions = FMutableMap<String, AppVersion>()
        val checksums = FMutableMap<String, C>()
        val strings = FMutableMap<String, String>()

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

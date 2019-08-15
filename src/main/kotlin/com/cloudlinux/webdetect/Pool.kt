package com.cloudlinux.webdetect

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet

typealias Checksum = ChecksumLong

typealias MutableSet<T> = ObjectOpenHashSet<T>
typealias MutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias MutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias SortedMap<K, V> = Object2ObjectRBTreeMap<K, V>
typealias SortedSet<T> = ObjectRBTreeSet<T>
typealias KMutableSet<T> = kotlin.collections.MutableSet<T>
typealias KMutableMap<K, V> = kotlin.collections.MutableMap<K, V>

class DataContext {
    val checksumToAppVersions: MutableMap<Checksum, MutableSet<AppVersion>> = MutableMap()
    val appVersions: MutableMap<AppVersion, MutableSet<Checksum>> = MutableMap()
    val pool = Pool()

    fun doPooling(
        app: String,
        version: String,
        checksum: String
    ) {
        val av = appVersion(app, version)
        val cs = checksum(checksum)
        appVersions.computeIfAbsent(av) { MutableSet() } += cs
        checksumToAppVersions.computeIfAbsent(cs) { MutableSet() } += av
    }

    fun cleanup() {
        checksumToAppVersions.clear()
        checksumToAppVersions.trim()
        appVersions.clear()
        appVersions.trim()
    }

    private fun checksum(cs: String): Checksum = pool.checksums.computeIfAbsent(cs) { it.asChecksumLong() }

    private fun string(string: String): String = pool.strings.computeIfAbsent(string) { string }

    private fun appVersion(app: String, version: String): AppVersion =
        pool.appVersions.computeIfAbsent(app + version) {
            AppVersion.Single(string(app), string(version), string(app + version))
        }

    class Pool {
        val appVersions = MutableMap<String, AppVersion>()
        val checksums = MutableMap<String, Checksum>()
        val strings = MutableMap<String, String>()

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

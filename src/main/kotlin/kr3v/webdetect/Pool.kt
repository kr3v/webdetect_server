package kr3v.webdetect

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
class WebdetectContext(val pathToCsv: String) {
    val csToAv: FMutableMap<Checksum, FMutableSet<AppVersion>> = FMutableMap()
    val avToCs: FMutableMap<AppVersion, FMutableSet<Checksum>> = FMutableMap()
    val pool = Pool()

    fun doPooling(
        app: String,
        version: String,
        checksum: String
    ) {
        val av = appVersion(app, version)
        val cs = checksum(checksum)

        avToCs.computeIfAbsent(av) { FMutableSet() } += cs
        csToAv.computeIfAbsent(cs) { FMutableSet() } += av
    }

    fun cleanup() {
        csToAv.clear()
        csToAv.trim()
        avToCs.clear()
        avToCs.trim()
    }

    private fun checksum(cs: String): Checksum =
        pool.cs.computeIfAbsent(string(cs)) { key -> ChecksumLong(key) }

    private fun string(string: String): String = pool.s.computeIfAbsent(string) { string }

    private fun appVersion(app: String, version: String): AppVersion =
        pool.av.computeIfAbsent(app + version) {
            AppVersion.Single(string(app), string(version), string(app + version))
        }

    /**
     * Separated from [WebdetectContext], as it can be cleaned-up a little bit earlier
     */
    class Pool {
        val av = FMutableMap<String, AppVersion>()
        val cs = FMutableMap<String, Checksum>()
        val s = FMutableMap<String, String>()

        fun cleanup() {
            av.clear()
            av.trim()
            cs.clear()
            cs.trim()
            s.clear()
            s.trim()
        }
    }
}

package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.Pool.appVersions
import com.cloudlinux.webdetect.Pool.checksums
import com.cloudlinux.webdetect.Pool.strings
import com.fasterxml.jackson.annotation.JsonIgnore
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

sealed class IAppVersion {
    fun apps(): List<String> = when (this) {
        is Single -> listOf(app)
        is Merged -> appVersions.map(IAppVersion::apps).flatten()
    }

    fun versions(): List<String> = when (this) {
        is Single -> listOf(version)
        is Merged -> appVersions.map(IAppVersion::versions).flatten()
    }

    data class Single(
        val app: String,
        val version: String,
        @field:JsonIgnore
        val both: String
    ) : IAppVersion() {
        override fun equals(other: Any?) = other === this || other is Single && other.both == both
        override fun hashCode() = both.hashCode()
        override fun toString() = "$app#$version"
    }

    data class Merged(
        val appVersions: MutableSet<IAppVersion>
    ): IAppVersion() {
        override fun toString(): String = appVersions.toString()
    }
}

typealias AppVersion = IAppVersion
typealias Checksum = String
//typealias Checksum = ChecksumLong

typealias MutableSet<T> = ObjectOpenHashSet<T>
typealias MutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias MutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias SortedSet<T> = ObjectOpenHashSet<T>

object Pool {
    val appVersions = MutableMap<String, AppVersion>()
    val checksums = MutableMap<String, Checksum>()
    val strings = MutableMap<String, String>()
}

//fun checksum(cs: String): Checksum = checksums.computeIfAbsent(cs) { it.asChecksumLong() }
fun checksum(cs: String) = checksums.computeIfAbsent(cs) { it }!!

fun string(string: String): String = strings.computeIfAbsent(string) { string }

fun appVersion(app: String, version: String): AppVersion =
    appVersions.computeIfAbsent(app + version) {
        IAppVersion.Single(
            string(app),
            string(version),
            string(app + version)
        )
    }!!

class PooledCtx {
    val checksumToAppVersions: MutableMap<Checksum, MutableSet<AppVersion>> = MutableMap()
    val appVersions: MutableSet<AppVersion> = MutableSet()

    fun doPooling(
        app: String,
        version: String,
        checksum: String
    ) {
        val av = appVersion(app, version)
        val cs = checksum(checksum)
        appVersions += av
        checksumToAppVersions.computeIfAbsent(cs) { MutableSet() } += av
    }
}
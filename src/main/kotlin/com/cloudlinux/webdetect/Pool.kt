package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.Pool.appVersions
import com.cloudlinux.webdetect.Pool.checksums
import com.cloudlinux.webdetect.Pool.strings
import com.fasterxml.jackson.annotation.JsonIgnore
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet


data class StringPair(
    val app: String,
    val version: String,
    @field:JsonIgnore
    val both: String
) {
    override fun equals(other: Any?) = other === this || other is StringPair && other.both == both
    override fun hashCode() = both.hashCode()
}

typealias AppVersion = StringPair
//typealias Checksum = String
typealias Checksum = ChecksumLong

typealias MutableSet<T> = ObjectOpenHashSet<T>
typealias MutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias MutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias SortedSet<T> = ObjectOpenHashSet<T>

object Pool {
    val appVersions = MutableMap<String, AppVersion>()
    val checksums = MutableMap<String, Checksum>()
    val strings = MutableMap<String, String>()
}

fun checksum(cs: String) = checksums.computeIfAbsent(cs) { it.asChecksumLong() }
//fun checksum(cs: String) = checksums.computeIfAbsent(cs) { it }!!
fun string(string: String) = strings.computeIfAbsent(string) { string }!!
fun appVersion(app: String, version: String) =
    appVersions.computeIfAbsent(app + version) { StringPair(string(app), string(version), string(app + version)) }!!
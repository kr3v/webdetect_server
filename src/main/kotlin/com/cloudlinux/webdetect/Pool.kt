package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.Pool.appVersions
import com.cloudlinux.webdetect.Pool.checksums
import com.cloudlinux.webdetect.Pool.strings
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet


typealias AppVersion = String
//typealias Checksum = String
typealias Checksum = ChecksumLong

typealias MutableSet<T> = ObjectOpenHashSet<T>
typealias MutableLinkedSet<T> = ObjectLinkedOpenHashSet<T>
typealias MutableMap<K, V> = Object2ObjectOpenHashMap<K, V>
typealias SortedSet<T> = ObjectOpenHashSet<T>

object Pool {
    val appVersions = MutableMap<AppVersion, AppVersion>()
    val checksums = MutableMap<String, Checksum>()
    val strings = MutableMap<String, String>()
}

fun string(string: String) = strings.computeIfAbsent(string) { string }
fun appVersion(app: String, version: String) = appVersions.computeIfAbsent(string(app + version)) { it }
//fun checksum(cs: String) = checksums.computeIfAbsent(cs) { it }
fun checksum(cs: String) = checksums.computeIfAbsent(cs) { it.asChecksumLong() }
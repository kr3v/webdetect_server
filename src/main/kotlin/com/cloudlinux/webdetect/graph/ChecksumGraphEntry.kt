package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.FMutableSet

interface ChecksumKey<C : ChecksumKey<C>> : Comparable<C> {
    fun asByteArray(): ByteArray
}

class ChecksumGraphEntry<C : ChecksumKey<C>>(
    val key: C,
    val appVersions: FMutableSet<AppVersionGraphEntry<C>>,
    val dependsOn: FMutableSet<AppVersionGraphEntry<C>> = FMutableSet(appVersions.size, 0.5f)
) : Comparable<ChecksumGraphEntry<C>> {
    override fun compareTo(other: ChecksumGraphEntry<C>) = key.compareTo(other.key)
    override fun equals(other: Any?) = this === other || other is ChecksumGraphEntry<*> && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

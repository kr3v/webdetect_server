package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableSet

class ChecksumGraphEntry(
    val key: Checksum,
    val appVersions: MutableSet<AppVersionGraphEntry>,
    val dependsOn: MutableSet<AppVersionGraphEntry>
) : Comparable<ChecksumGraphEntry> {
    override fun compareTo(other: ChecksumGraphEntry) = key.compareTo(other.key)
    override fun equals(other: Any?) = this === other || other is ChecksumGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}
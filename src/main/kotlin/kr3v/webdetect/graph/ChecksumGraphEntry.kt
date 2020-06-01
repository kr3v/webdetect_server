package kr3v.webdetect.graph

import kr3v.webdetect.Checksum
import kr3v.webdetect.FMutableSet

class ChecksumGraphEntry(
    val key: Checksum,
    val appVersions: FMutableSet<AppVersionGraphEntry>,
    val dependsOn: FMutableSet<AppVersionGraphEntry> = FMutableSet(appVersions.size, 0.5f)
) : Comparable<ChecksumGraphEntry> {
    override fun compareTo(other: ChecksumGraphEntry) = key.compareTo(other.key)
    override fun equals(other: Any?) = this === other || other is ChecksumGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableSet

private val EMPTY_INT_ARRAY = IntArray(0)

/**
 * @property key app-version holder
 * @property checksums checksums that are present in app-version; after GraphBasedSolution - checksums that define this app-version
 * @property implies all such app-versions, which checksums are subset of [checksums]
 * @property properties task-specific array of dynamic integer properties; this is expected to simplify caching of values without need to add new field
 */
class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: FMutableSet<ChecksumGraphEntry>,
    val released: FMutableSet<ChecksumGraphEntry> = FMutableSet(checksums.size, 0.5f),
    val implies: FMutableSet<AppVersionGraphEntry> = FMutableSet(),
    override var properties: IntArray = EMPTY_INT_ARRAY
) : HasIntProperties {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

interface HasIntProperties {
    var properties: IntArray
}

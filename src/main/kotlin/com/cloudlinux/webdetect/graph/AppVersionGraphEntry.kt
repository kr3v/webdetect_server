package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableSet

class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: MutableSet<ChecksumGraphEntry>,
    override var intField: Int = -1
) : HasIntField {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

interface HasIntField {
    var intField: Int
}

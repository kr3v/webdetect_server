package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableSet

private val EMPTY_INT_ARRAY = IntArray(0)

class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: MutableSet<ChecksumGraphEntry>,
    override var properties: IntArray = EMPTY_INT_ARRAY
) : HasIntProperties {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

interface HasIntProperties {
    var properties: IntArray
}

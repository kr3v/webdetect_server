package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.ChecksumKey

data class MethodName(
    val key: String,
    val path: String?
) : ChecksumKey<MethodName> {
    override fun equals(other: Any?) = this === other || other is MethodName && this.key == other.key
    override fun hashCode() = key.hashCode()
    override fun compareTo(other: MethodName) = key.compareTo(other.key)
    override fun asByteArray(): ByteArray = key.toByteArray()
    override fun toString() = key
}

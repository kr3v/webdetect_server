package com.cloudlinux.webdetect

import java.lang.Character.digit
import java.nio.ByteBuffer
import java.util.Arrays

fun String.asChecksumLong(): ChecksumLong {
    if (this.length != 64) throw Exception(this)
    val ll = LongArray(4)
    for (i in 0..3) {
        var l = 0L
        for (j in 0..15) {
            l = 16 * l + digit(this[16 * i + j], 16)
        }
        ll[i] = l
    }
    return ChecksumLong(ll)
}

data class ChecksumLong(
    val ll: LongArray,
    val hash: Int = Arrays.hashCode(ll)
) : Comparable<ChecksumLong> {

    override fun compareTo(other: ChecksumLong): Int {
        for (idx in ll.indices) {
            val compareTo = ll[idx].compareTo(other.ll[idx])
            if (compareTo != 0) return compareTo
        }
        return 0
    }

    override fun equals(other: Any?) = this === other || other is ChecksumLong && Arrays.equals(ll, other.ll)
    override fun hashCode() = hash
    override fun toString(): String = asHexString()

    fun asByteArray(): ByteArray {
        val result = ByteArray(32)
        val bb = ByteBuffer.wrap(result)
        ll.forEach { bb.putLong(it) }
        return result
    }

    private fun asHexString() = ll.indices.joinToString(separator = "") { String.format("%016x", ll[it]) }
}

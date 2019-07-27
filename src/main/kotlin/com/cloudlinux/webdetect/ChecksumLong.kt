package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.bloomfilter.murmurHash
import java.nio.ByteBuffer
import java.util.Arrays

fun hexDigit(c: Char) = when (c) {
    in '0'..'9' -> c.toInt() - '0'.toInt()
    else -> c.toInt() - 'a'.toInt() + 10
}

fun String.asChecksumLong(): ChecksumLong {
    if (this.length != 64) throw Exception(this)
    val ll = LongArray(4)
    for (i in 0..3) {
        var l = 0L
        for (j in 0..15) {
            l = 16 * l + hexDigit(this[16 * i + j])
        }
        ll[i] = l
    }
    return ChecksumLong(ll)
}

data class ChecksumLong(
    val ll: LongArray,
    val hash: Int = Arrays.hashCode(ll)
) : Comparable<ChecksumLong> {

    val bloomFilterHash1: Long
    val bloomFilterHash2: Long
    val byteArray: ByteArray

    init {
        val (h1, h2) = murmurHash(this.asByteArray())
        bloomFilterHash1 = h1
        bloomFilterHash2 = h2
        byteArray = asByteArray()
    }

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

    private fun asByteArray(): ByteArray {
        val result = ByteArray(32)
        val bb = ByteBuffer.wrap(result)
        ll.forEach { bb.putLong(it) }
        return result
    }

    private fun asHexString() = ll.joinToString(separator = "") { String.format("%016x", it) }
}

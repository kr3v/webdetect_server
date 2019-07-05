package com.cloudlinux.webdetect

import java.lang.Character.digit
import java.lang.Long.compareUnsigned
import java.util.Objects

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
    return ChecksumLong(
        ll[0],
        ll[1],
        ll[2],
        ll[3]
    )
}

@Suppress("EqualsOrHashCode")
data class ChecksumLong(
    val l1: Long,
    val l2: Long,
    val l3: Long,
    val l4: Long,
    val hash: Int = Objects.hash(l1, l2, l3, l4)
) : Comparable<ChecksumLong> {

    override fun compareTo(other: ChecksumLong): Int {
        val ll1 = compareUnsigned(l1, other.l1)
        return if (ll1 == 0) {
            val ll2 = compareUnsigned(l2, other.l2)
            if (ll2 == 0) {
                val ll3 = compareUnsigned(l3, other.l3)
                if (ll3 == 0) compareUnsigned(l4, other.l4)
                else ll3
            } else ll2
        } else ll1
    }

    override fun hashCode() = hash
}
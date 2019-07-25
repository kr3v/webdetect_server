package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.Checksum
import orestes.bloomfilter.FilterBuilder
import java.util.BitSet

data class ImmutableBloomFilter(
    val bloom: BitSet,
    val config: FilterBuilder,
    val items: MutableSet<Checksum>
) {
    fun contains(hash1: Long, hash2: Long): Boolean {
        val k = config.hashes()
        val m = config.size()
        for (i in 0 until k) {
            val position = ((hash1 + i * hash2) % m).toInt()
            if (!getBit(position)) {
                return false
            }
        }
        return true
    }

    private fun getBit(index: Int) = bloom.get(index)
}
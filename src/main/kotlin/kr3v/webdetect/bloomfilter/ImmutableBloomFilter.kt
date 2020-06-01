package kr3v.webdetect.bloomfilter

import kr3v.webdetect.Checksum
import kr3v.webdetect.ChecksumLong
import orestes.bloomfilter.FilterBuilder
import java.util.BitSet

// @NotThreadSafe
data class ImmutableBloomFilter(
    val bloom: BitSet,
    val config: FilterBuilder,
    val items: MutableSet<Checksum>,
    val type: HashingType
) {
    private val bitsInFilterSize = 32 - Integer.numberOfLeadingZeros(config.size())

    fun addRaw(csl: ChecksumLong) = when (type) {
        HashingType.SHA256 -> addRawSha256(csl.byteArray)
        HashingType.MURMUR -> addRawMurmur(csl.bloomFilterHash1, csl.bloomFilterHash2)
    }

    operator fun contains(csl: ChecksumLong) = when (type) {
        HashingType.SHA256 -> containsSha256(csl.byteArray)
        HashingType.MURMUR -> containsMurmur(csl.bloomFilterHash1, csl.bloomFilterHash2)
    }

    private fun addRawMurmur(hash1: Long, hash2: Long) {
        murmur(hash1, hash2) { position -> setBit(position, true) }
    }

    private fun containsMurmur(hash1: Long, hash2: Long): Boolean {
        murmur(hash1, hash2) { position ->
            if (!getBit(position)) {
                return false
            }
        }
        return true
    }

    private fun addRawSha256(ba: ByteArray) {
        sha256(ba) { position -> setBit(position, true) }
    }

    private fun containsSha256(ba: ByteArray): Boolean {
        sha256(ba) { position ->
            if (!getBit(position)) {
                return false
            }
        }
        return true
    }

    private inline fun murmur(hash1: Long, hash2: Long, fn: (Int) -> Unit) {
        val k = config.hashes()
        val m = config.size()
        for (i in 0 until k) {
            val position = ((hash1 + i * hash2) % m).toInt()
            fn(position)
        }
    }

    // is this wrong? we take [bitsInFilterSize], but iterating through *Byte*Array (like 8x overhead?)
    private inline fun sha256(ba: ByteArray, fn: (Int) -> Unit) {
        val k = config.hashes()
        val m = config.size()

        var baIdx = 0
        for (i in 0 until k) {
            var position = 0L
            for (j in 0 until bitsInFilterSize) {
                position = position * 256 + (ba[baIdx++] + 128)
                if (baIdx == ba.size) {
                    baIdx %= ba.size
                }
            }
            if (position < 0) {
                position += Long.MAX_VALUE
                position += 1
            }
            fn((position % m).toInt())
        }
    }

    private fun getBit(index: Int) = bloom.get(index)
    private fun setBit(index: Int, state: Boolean) = bloom.set(index, state)

    enum class HashingType {
        SHA256,
        MURMUR
    }
}

package kr3v.webdetect.util

import java.nio.ByteBuffer

fun IntArray.asByteArray(): ByteArray {
    val ba = ByteArray(size * 4)
    val bb = ByteBuffer.wrap(ba)
    this.forEach { bb.putInt(it) }
    return ba
}

fun Int.asByteArray() = ByteArray(4).also { ByteBuffer.wrap(it).putInt(this) }
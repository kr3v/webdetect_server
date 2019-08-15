package com.cloudlinux.webdetect.graph.pq

import com.cloudlinux.webdetect.graph.HasIntProperties
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class PriorityQueueTest {

    data class KInt(
        var value: Int,
        override var properties: IntArray
    ) : HasIntProperties {
        override fun equals(other: Any?) = this === other || other is KInt && other.value == this.value
        override fun hashCode(): Int = value
    }

    @Test
    fun test() {
        val values = (-20..20).map { KInt(it, intArrayOf(1)) }
        val ordered = values.map { it.value }.sortedDescending()
        repeat(1000000) { _ ->
            val pq = PriorityQueue(values.shuffled(), compareBy { it.value })
            val it = sequence { while (pq.isNotEmpty()) yield(pq.pop()) }
            Assertions.assertThat(it.toList().map { it.value }).isEqualTo(ordered)
        }
    }

    @Test
    fun testInc() {
        val values = (-20..20).map { KInt(it, intArrayOf(1)) }
        repeat(1000000) { _ ->
            val vv = values.shuffled()
            val pq = PriorityQueue(vv, compareBy { it.value })

            val mn = vv.minBy { it.value }!!
            val mx = vv.maxBy { it.value }!!

            mn.value += 100
            pq.update(mn)
            mx.value -= 100
            pq.update(mx)

            val it = sequence { while (pq.isNotEmpty()) yield(pq.pop()) }
            Assertions.assertThat(it.toList().map { it.value }).isEqualTo(vv.map { it.value }.sortedDescending())
        }
    }
}
package com.cloudlinux.webdetect.graph.pq

import com.cloudlinux.webdetect.graph.HasIntProperties
import java.util.Comparator

var HasIntProperties.pqIndex
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

internal class PriorityQueue<T : HasIntProperties> private constructor(
    private val values: MutableList<T>,
    private val cmp: Comparator<T>,
    private var last: Int
) {

    constructor(values: Collection<T>, cmp: Comparator<T>) : this(
        values.mapIndexedTo(ArrayList(values.size)) { idx, it ->
            it.pqIndex = idx
            it
        },
        cmp,
        values.size - 1
    )

    init {
        for (it in values.indices) {
            up(it)
        }
    }

    private operator fun T.compareTo(rhs: T) = cmp.compare(this, rhs)

    fun pop(): T {
        swap(0, last--)
        down(0)
        return values[last + 1]
    }

    fun update(t: T) {
        up(t.pqIndex)
        down(t.pqIndex)
    }

    fun isEmpty() = last == -1
    fun isNotEmpty() = last >= 0

    @Suppress("NAME_SHADOWING")
    private fun up(idx: Int) {
        var idx = idx
        while (idx != 0) {
            val parent = (idx - 1) / 2
            if (values[idx] <= values[parent]) break
            idx = swap(idx, parent)
        }
    }

    @Suppress("NAME_SHADOWING", "SameParameterValue")
    private fun down(idx: Int) {
        var idx = idx
        while (idx * 2 + 1 <= last) {
            val idxValue = values[idx]
            val child1 = idx * 2 + 1
            val child2 = idx * 2 + 2

            if (child2 > last) {
                if (values[child1] > idxValue)
                    swap(idx, child1)
                break
            }

            idx = when {
                idxValue < values[child1] && values[child1] >= values[child2] -> swap(idx, child1)
                idxValue < values[child2] && values[child2] >= values[child1] -> swap(idx, child2)
                else -> return
            }
        }
    }

    private fun swap(idx1: Int, idx2: Int): Int {
        values[idx1].pqIndex = idx2
        values[idx2].pqIndex = idx1

        val temp = values[idx1]
        values[idx1] = values[idx2]
        values[idx2] = temp
        return idx2
    }
}

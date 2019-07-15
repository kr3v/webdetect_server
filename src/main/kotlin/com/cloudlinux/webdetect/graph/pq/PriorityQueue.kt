package com.cloudlinux.webdetect.graph.pq

import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.HasIntField


internal class PriorityQueue<T : HasIntField> private constructor(
    private val values: MutableList<Entry<T>>,
    private var last: Int = values.size - 1
) {

    constructor(values: Collection<T>, keyExtractor: (T) -> Int) : this(
        values.mapIndexedTo(ArrayList(values.size)) { idx, it ->
            it.intField = idx
            Entry(keyExtractor(it), it)
        })

    init {
        for (it in values.indices) {
            up(it)
        }
    }

    fun pop(): Entry<T> {
        swap(0, last--)
        down(0)
        return values[last + 1]
    }

    fun inc(t: T, delta: Int) {
        values[t.intField].key += delta
        up(t.intField)
    }

    fun isEmpty() = last == -1
    fun isNotEmpty() = last >= 0

    @Suppress("NAME_SHADOWING")
    private fun up(idx: Int) {
        var idx = idx
        while (idx != 0) {
            val parent = (idx - 1) / 2
            if (values[idx].key <= this.values[parent].key) break
            swap(idx, parent)
            idx = parent
        }
    }

    @Suppress("NAME_SHADOWING", "SameParameterValue")
    private fun down(idx: Int) {
        var idx = idx
        while (idx != 0) {
            val child = idx * 2 + 1
            idx = when {
                values[idx].key < this.values[child].key -> child.also { swap(idx, it) }
                values[idx].key < this.values[child + 1].key -> (child + 1).also { swap(idx, it) }
                else -> return
            }
        }
    }

    private fun swap(idx1: Int, idx2: Int) {
        values[idx1].value.intField = idx2
        values[idx2].value.intField = idx1

        val temp = values[idx1]
        values[idx1] = values[idx2]
        values[idx2] = temp
    }

    data class Entry<T : HasIntField>(
        var key: Int,
        val value: T
    )
}
package com.cloudlinux.webdetect.util

import it.unimi.dsi.fastutil.ints.IntIterable
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.util.stream.Collectors
import java.util.stream.Stream

fun <T> exactlyOneIntersect(lhs: Set<T>, rhs: Set<T>) =
    if (lhs.size > rhs.size) exactlyOneIntersectImpl(rhs, lhs) else exactlyOneIntersectImpl(lhs, rhs)

private fun <T> exactlyOneIntersectImpl(lhs: Set<T>, rhs: Set<T>): Boolean {
    var found = false
    for (l in lhs)
        if (l in rhs)
            if (found) return false
            else found = true
    return found
}

fun <T> countIntersects(lhs: Set<T>, rhs: Set<T>) =
    if (lhs.size > rhs.size) countIntersectsImpl(rhs, lhs) else countIntersectsImpl(lhs, rhs)

private fun <T> countIntersectsImpl(lhs: Set<T>, rhs: Set<T>): Int {
    var res = 0
    for (l in lhs)
        if (l in rhs)
            res++
    return res
}

fun <T> Stream<T>.toList(size: Int): ObjectArrayList<T> = collect(Collectors.toCollection { ObjectArrayList<T>(size) })

inline fun IntIterable.intForEach(fn: (Int) -> Unit) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        fn(iterator.nextInt())
    }
}

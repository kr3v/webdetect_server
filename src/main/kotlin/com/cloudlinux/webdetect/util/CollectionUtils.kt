package com.cloudlinux.webdetect.util

import com.cloudlinux.webdetect.graph.AppVersionGraphEntry

fun exactlyOneIntersect(lhs: Set<AppVersionGraphEntry>, rhs: Set<AppVersionGraphEntry>) =
    if (lhs.size > rhs.size) exactlyOneIntersectImpl(rhs, lhs) else exactlyOneIntersectImpl(lhs, rhs)

private fun exactlyOneIntersectImpl(
    lhs: Set<AppVersionGraphEntry>,
    rhs: Set<AppVersionGraphEntry>
): Boolean {
    var found = false
    for (l in lhs)
        if (l in rhs)
            if (found) return false
            else found = true
    return found
}

fun countIntersects(lhs: Set<AppVersionGraphEntry>, rhs: Set<AppVersionGraphEntry>) =
    if (lhs.size > rhs.size) countIntersectsImpl(rhs, lhs) else countIntersectsImpl(lhs, rhs)

private fun countIntersectsImpl(
    lhs: Set<AppVersionGraphEntry>,
    rhs: Set<AppVersionGraphEntry>
): Int {
    var res = 0
    for (l in lhs)
        if (l in rhs)
            res++
    return res
}
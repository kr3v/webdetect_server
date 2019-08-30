package com.cloudlinux.webdetect

import java.io.File

inline fun read(
    path: String,
    separator: String,
    crossinline withoutPathHandler: (List<String>) -> Unit,
    crossinline withPathHandler: (List<String>) -> Unit
) {
    File(path).forEachLine { line ->
        val row = line.split(separator).dropLastWhile(String::isBlank)
        when {
            row.size == 3 -> withoutPathHandler(row)
            row.size == 4 -> withPathHandler(row)
            else -> throw Exception("Unknown row format: ${row.size} -> $row")
        }
    }
}

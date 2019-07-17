package com.cloudlinux.webdetect

import java.io.File

fun <T> read(
    path: String,
    separator: String,
    rowHandler: (List<String>) -> T,
    avChecksumHandler: (T) -> Unit
) {
    val csvFile = File(path)

    csvFile.forEachLine { line ->
        val row = line.split(separator).dropLastWhile(String::isBlank)
        val t = rowHandler(
            when {
                row.size > 3 -> listOf(
                    row[0],
                    (0 until row.lastIndex).joinToString(separator = ".") { row[it] },
                    row.last()
                )
                row.size == 3 -> row
                else -> throw Exception("${row.size} -> $row")
            }
        )
        avChecksumHandler(t)
    }
}

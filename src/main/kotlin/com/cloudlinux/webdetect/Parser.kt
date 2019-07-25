package com.cloudlinux.webdetect

import java.io.File

fun read(
    path: String,
    separator: String,
    avChecksumHandler: (List<String>) -> Unit
) {
    File(path).forEachLine { line ->
        val row = line.split(separator).dropLastWhile(String::isBlank)
        when {
            row.size == 3 -> avChecksumHandler(row)
            else -> throw Exception("Unknown row format: ${row.size} -> $row")
        }
    }
}

package kr3v.webdetect

import java.io.File

inline fun read(
    path: String,
    separator: String,
    crossinline handler: (List<String>) -> Unit
) {
    File(path).forEachLine { line ->
        val row = line.split(separator).dropLastWhile(String::isBlank)
        when (row.size) {
            in 3..5 -> handler(row)
            else -> throw Exception("Unknown row format: ${row.size} -> $row")
        }
    }
}

package com.cloudlinux.webdetect

import java.io.File

interface Splitter {
    fun split(values: List<String>): Pair<List<String>, List<String>>
    fun isValid(values: List<String>): Boolean
}

inline fun read(
    path: String,
    separator: String,
    splitter: Splitter,
    crossinline rowsHandler: (av: List<String>, cs: List<String>) -> Unit
) {
    File(path).forEachLine { line ->
        val items = line.split(separator).dropLastWhile(String::isBlank)
        val (av, cs) = splitter.split(items)
        rowsHandler(av, cs)
    }
}

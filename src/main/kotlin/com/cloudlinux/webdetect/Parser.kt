package com.cloudlinux.webdetect

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import java.io.File


private val mapper = CsvMapper().also {
    it.enable(CsvParser.Feature.WRAP_AS_ARRAY)
}

fun <T> read(
    path: String,
    separator: Char,
    rowHandler: (List<String>) -> T,
    avChecksumHandler: (T) -> Unit
) {
    val csvFile = File(path)
    val schema = mapper.schemaFor(Array<String>::class.java).withColumnSeparator(separator)
    val it = mapper.readerFor(Array<String>::class.java).with(schema).readValues<Array<String>>(csvFile)

    while (it.hasNext()) {
        val row = it.next().dropLastWhile { it.isBlank() }
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

package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.ChecksumKey
import java.time.ZonedDateTime


object AppVersionFactory : EntityFactory<String, String, AppVersion> {
    override fun constructor(values: List<String>, cached: String.() -> String) = AppVersion.Single(
        values[0].cached(),
        values[1].cached(),
        values.getOrNull(2)?.cached(),
        (values[0] + values[1]).cached()
    )

    override fun key(values: List<String>) = values[0] + values[1]
    override fun isValid(values: List<String>) = values.size in 2..3
}

interface CObjects<C : ChecksumKey<C>> {
    val factory: EntityFactory<String, String, C>
    val splitter: Splitter
}

object MethodNameObjects : CObjects<MethodName> {
    object Factory : EntityFactory<String, String, MethodName> {
        override fun constructor(values: List<String>, cached: String.() -> String) = MethodName(
            values[0].toLowerCase().cached(),
            values.getOrNull(1)?.cached()
        )

        override fun key(values: List<String>) = values.first()
        override fun isValid(values: List<String>) = values.size in 1..2
    }

    object Splitter : com.cloudlinux.webdetect.Splitter {
        override fun split(values: List<String>) = Pair(
            values.take(2),
            values.drop(2)
        )

        override fun isValid(values: List<String>) = values.size in 3..5
    }

    override val factory get() = Factory
    override val splitter get() = Splitter
}

object ChecksumObjects : CObjects<Checksum> {
    object Factory : EntityFactory<String, String, Checksum> {
        override fun constructor(values: List<String>, cached: String.() -> String) = ChecksumLong(
            values[0],
            values.getOrNull(1)?.cached()
        )

        override fun key(values: List<String>) = values.first()
        override fun isValid(values: List<String>) = values.size in 1..2
    }

    object Splitter : com.cloudlinux.webdetect.Splitter {
        override fun split(values: List<String>) = Pair(
            values.take(2),
            values.drop(2)
        )

        override fun isValid(values: List<String>) = values.size in 3..4
    }

    override val factory get() = Factory
    override val splitter get() = Splitter
}

fun <C : ChecksumKey<C>> buildContextByCsv(`in`: String, objects: CObjects<C>): WebdetectContext<C> {
    val webdetectCtx = WebdetectContext(AppVersionFactory, objects.factory)
    println("${ZonedDateTime.now()}: $`in` processing started")
    read(
        path = `in`,
        separator = "\t",
        splitter = objects.splitter,
        rowsHandler = webdetectCtx::doPooling
    )
    println("${ZonedDateTime.now()}: $`in` processing done")
    webdetectCtx.pool.cleanup()
    return webdetectCtx
}

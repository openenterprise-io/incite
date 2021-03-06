package io.openenterprise.incite.data.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(value = FileSource::class, name = "FileSource"),
        JsonSubTypes.Type(value = JdbcSource::class, name = "JdbcSource"),
        JsonSubTypes.Type(value = KafkaSource::class, name = "KafkaSource")
    ]
)
abstract class Source: Cloneable {

    var fields: MutableSet<Field>? = null

    var watermark: Watermark? = null

    public override fun clone(): Any {
        return super.clone()
    }

    class Watermark() {

        constructor(eventTimeColumn: String, delayThreshold: String): this() {
            this.delayThreshold = delayThreshold
            this.eventTimeColumn = eventTimeColumn
        }

        lateinit var eventTimeColumn: String

        lateinit var delayThreshold: String
    }
}

class FileSource: StreamingSource() {

    lateinit var path: String

    var format: Format = Format.Json

    var maxFilesPerTrigger: Long = 1

    var latestFirst: Boolean = false

    var maxFileAge: String = "7d"

    var cleanSource: CleanSourceOption = CleanSourceOption.Off

    var sourceArchiveDirectory: String? = null

    enum class CleanSourceOption {

        Archive, Delete, Off
    }

    enum class Format {

        Json
    }
}

class JdbcSource: Source() {

    lateinit var rdbmsDatabase: RdbmsDatabase

    lateinit var query: String
}

class KafkaSource: StreamingSource() {

    lateinit var kafkaCluster: KafkaCluster

    var startingOffset: String = if (streamingRead) "latest" else "earliest"

    lateinit var topic: String
}

abstract class StreamingSource: Source() {

    var streamingRead: Boolean = true
}
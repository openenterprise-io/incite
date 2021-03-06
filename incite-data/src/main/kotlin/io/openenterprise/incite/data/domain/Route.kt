package io.openenterprise.incite.data.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.openenterprise.data.domain.AbstractMutableEntity
import java.util.*
import javax.persistence.*

@MappedSuperclass
@DiscriminatorColumn(name = "type")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(value = SpringXmlRoute::class, name = "SpringXmlRoute"),
        JsonSubTypes.Type(value = YamlRoute::class, name = "YamlRoute")
    ]
)
abstract class Route : AbstractMutableEntity<String>() {

    @Version
    var version: Long? = null

    @PrePersist
    override fun prePersist() {
        super.prePersist()

        id = UUID.randomUUID().toString()
    }

    enum class Type {

        SPRING_XML, YAML
    }
}

@Entity
@DiscriminatorValue("SpringXML")
class SpringXmlRoute : Route() {

    lateinit var xml: String
}

@Entity
@DiscriminatorValue("YAML")
class YamlRoute: Route() {

    lateinit var yaml: String
}
package io.openenterprise.incite.data.domain

import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("YAML")
class YamlRoute: Route() {

    lateinit var yaml: String

}
package io.openenterprise.incite.data.domain

import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("SpringXML")
class SpringXmlRoute : Route() {

    var xml: String? = null

}
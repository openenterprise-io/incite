package io.openenterprise.incite

import org.apache.ignite.springframework.boot.autoconfigure.IgniteAutoConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jersey.JerseyAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan

@SpringBootConfiguration
@ComponentScan(
    basePackages = [
        "io.openenterprise.springframework.boot.autoconfigure",
        "io.openenterprise.incite.context",
        "org.apache.camel.spring.boot"
    ]
)
@EnableAutoConfiguration(exclude = [IgniteAutoConfiguration::class])
@ImportAutoConfiguration(
    classes = [
        DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class, JerseyAutoConfiguration::class,
        ServletWebServerFactoryAutoConfiguration::class
    ]
)
class SpringApplication : SpringBootServletInitializer() {

    companion object {

        @JvmStatic
        @kotlin.jvm.Throws(Exception::class)
        fun main(vararg args: String) {
            org.springframework.boot.SpringApplication.run(SpringApplication::class.java, *args)
        }
    }

    override fun configure(builder: SpringApplicationBuilder?): SpringApplicationBuilder {
        return builder!!.sources(SpringApplication::class.java)
    }
}
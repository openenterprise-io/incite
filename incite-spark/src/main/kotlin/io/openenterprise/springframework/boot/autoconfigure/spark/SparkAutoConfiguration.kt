package io.openenterprise.springframework.boot.autoconfigure.spark

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.lang.IllegalArgumentException
import java.util.*

@Configuration
@ConditionalOnClass(SparkContext::class)
@EnableConfigurationProperties(SparkProperties::class)
class SparkAutoConfiguration {

    @Autowired
    protected lateinit var sparkProperties: SparkProperties

    @kotlin.jvm.Throws(IllegalArgumentException::class)
    @Bean
    @ConditionalOnMissingBean(SparkConf::class)
    protected fun sparkConf(): SparkConf {
        Optional.ofNullable(sparkProperties.appName).orElseThrow{ IllegalArgumentException() }
        Optional.ofNullable(sparkProperties.master).orElseThrow{ IllegalArgumentException() }

        val sparkConf = SparkConf()
        sparkConf.setAppName(sparkProperties.appName)
        sparkConf.setMaster(sparkProperties.master)

        sparkProperties.executor.entries.stream().forEach {
            sparkConf.set("spark.executor.${it.key}", it.value.toString())
        }

        sparkProperties.hadoop.entries.stream().forEach {
            sparkConf.set("spark.hadoop.${it.key}", it.value.toString())
        }

        sparkProperties.memory.entries.stream().forEach {
            sparkConf.set("spark.memory.${it.key}", it.value.toString())
        }

        sparkProperties.sql.entries.stream().forEach {
            sparkConf.set("spark.sql.${it.key}", it.value.toString())
        }

        return sparkConf
    }

    @Bean
    @ConditionalOnBean(SparkConf::class)
    protected fun sparkSession(sparkConf: SparkConf): SparkSession {
        return SparkSession.builder().config(sparkConf).orCreate
    }
}
package io.openenterprise.incite.context

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.ignite.cache.CachingProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.cache.CacheManager

@Configuration
class ApplicationConfiguration {

    @Bean
    fun cachingProvider(): CacheManager {
        return CachingProvider().cacheManager
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .findAndRegisterModules()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
    }
}
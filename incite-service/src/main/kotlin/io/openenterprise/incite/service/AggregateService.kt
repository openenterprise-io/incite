package io.openenterprise.incite.service

import io.openenterprise.incite.data.domain.Aggregate
import io.openenterprise.incite.AggregateContext
import io.openenterprise.service.AbstractMutableEntityService
import org.apache.ignite.cache.query.annotations.QuerySqlFunction

interface AggregateService: AbstractMutableEntityService<Aggregate, String> {

    companion object {

        @JvmStatic
        @QuerySqlFunction(alias = "aggregate")
        fun aggregate(): String = TODO()
    }

    /**
     * Aggregate with given [Aggregate] (definition) instance.
     *
     * @param aggregate The [Aggregate] (definition) instance
     * @return The updated but not yet merged Aggregate (definition) instance
     */
    fun aggregate(aggregate: Aggregate): Aggregate

    /**
     * Get the context by ID of the defined [Aggregate] (definition) instance.
     *
     * @param id The ID of the defined [Aggregate] (definition) instance.
     */
    fun getContext(id: String): AggregateContext?

    /**
     * Stop an [Aggregate] which is streaming. An [Aggregate] is considered as a streaming aggregate if it has one or
     * more [io.openenterprise.incite.data.domain.StreamingSink].
     *
     * @param id The id of the streaming aggregate
     * @throws UnsupportedOperationException If the corresponding aggregate is not a streaming aggregate
     */
    fun stopStreaming(id: String): Boolean
}
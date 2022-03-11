package io.openenterprise.incite.ml.ws.rs

import io.openenterprise.incite.data.domain.Clustering
import io.openenterprise.incite.ml.service.ClusteringService
import io.openenterprise.ws.rs.AbstractAbstractMutableEntityResourceImpl
import kotlinx.coroutines.launch
import javax.inject.Named
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType

@Named
@Path("/cluster-analyses")
class ClusteringResourceImpl : ClusteringResource,
    AbstractAbstractMutableEntityResourceImpl<Clustering, String>() {

    @GET
    @Path("/{id}/model")
    @Produces(MediaType.APPLICATION_JSON)
    override fun buildModel(@PathParam("id") id: String, @Suspended asyncResponse: AsyncResponse) {
        coroutineScope.launch {
            val modelId = ClusteringService.buildModel(id)

            asyncResponse.resume(modelId)
        }
    }

    @POST
    @Path("/{id}/predict")
    @Consumes(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    override fun predict(@PathParam("id") id: String, jsonOrSql: String, @Suspended asyncResponse: AsyncResponse) {
        coroutineScope.launch {
            val result = ClusteringService.predict(id, jsonOrSql)

            asyncResponse.resume(result)
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    override fun create(entity: Clustering, @Suspended asyncResponse: AsyncResponse) {
        super.create(entity, asyncResponse)
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    override fun retrieve(@PathParam("id") id: String, @Suspended asyncResponse: AsyncResponse) {
        super.retrieve(id, asyncResponse)
    }

    @DELETE
    @Path("/{id}")
    override fun delete(@PathParam("id") id: String, @Suspended asyncResponse: AsyncResponse) {
        super.delete(id, asyncResponse)
    }

    @PATCH
    @Path("/{id}")
    override fun update(id: String, entity: Clustering, asyncResponse: AsyncResponse) {
        super.update(id, entity, asyncResponse)
    }
}
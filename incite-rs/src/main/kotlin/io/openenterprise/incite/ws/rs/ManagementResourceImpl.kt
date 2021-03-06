package io.openenterprise.incite.ws.rs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.ignite.IgniteCluster
import org.apache.ignite.IgniteException
import org.apache.ignite.cluster.ClusterState
import org.springframework.beans.factory.annotation.Autowired
import javax.inject.Named
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Response

@Named
@Path("/management")
class ManagementResourceImpl : ManagementResource {

    @Autowired
    private lateinit var coroutineScope: CoroutineScope

    @Autowired
    private lateinit var igniteCluster: IgniteCluster

    @Path("/ignite/cluster-state/{clusterState}")
    @POST
    override fun setIgniteClusterState(
        @PathParam("clusterState") clusterState: ClusterState,
        @Suspended asyncResponse: AsyncResponse
    ) {
        coroutineScope.launch {
            try {
                igniteCluster.state(clusterState)
            } catch (igniteException: IgniteException) {
                asyncResponse.resume(igniteException)
            }

            asyncResponse.resume(Response.ok().build())
        }
    }
}
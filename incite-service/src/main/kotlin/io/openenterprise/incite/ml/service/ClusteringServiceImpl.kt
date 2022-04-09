package io.openenterprise.incite.ml.service

import io.openenterprise.ignite.cache.query.ml.ClusteringFunction
import io.openenterprise.incite.data.domain.BisectingKMeans
import io.openenterprise.incite.data.domain.Clustering
import io.openenterprise.incite.data.domain.KMeans
import io.openenterprise.incite.service.AggregateService
import io.openenterprise.incite.service.AggregateServiceImpl
import io.openenterprise.incite.spark.sql.service.DatasetService
import org.apache.spark.ml.Model
import org.apache.spark.ml.clustering.BisectingKMeansModel
import org.apache.spark.ml.clustering.KMeansModel
import org.apache.spark.ml.util.MLWritable
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Named
import javax.persistence.EntityNotFoundException

@Named
class ClusteringServiceImpl(
    @Inject private val aggregateService: AggregateService,
    @Inject private val datasetService: DatasetService,
    @Inject private val clusteringFunction: ClusteringFunction,
    @Inject private val transactionTemplate: TransactionTemplate
) :
    ClusteringService,
    AbstractMachineLearningServiceImpl<Clustering, ClusteringFunction>(
        aggregateService,
        datasetService,
        clusteringFunction
    ) {

    override fun <M : Model<M>> buildModel(entity: Clustering): M {
        val dataset = getAggregatedDataset(entity)

        @Suppress("UNCHECKED_CAST")
        return when (val algorithm = entity.algorithm) {
            is Clustering.FeatureColumnsBasedAlgorithm -> {
                when (algorithm) {
                    is BisectingKMeans -> {
                        clusteringFunction.buildBisectingKMeansModel(
                            dataset,
                            algorithm.featureColumns.stream().collect((Collectors.joining(","))),
                            algorithm.k,
                            algorithm.maxIteration,
                            algorithm.seed
                        )
                    }
                    is KMeans -> {
                        clusteringFunction.buildKMeansModel(
                            dataset,
                            algorithm.featureColumns.stream().collect((Collectors.joining(","))),
                            algorithm.k,
                            algorithm.maxIteration,
                            algorithm.seed
                        )
                    }
                    else -> throw UnsupportedOperationException()
                }
            }
            else -> throw UnsupportedOperationException()
        } as M
    }

    override fun persistModel(entity: Clustering, sparkModel: MLWritable): UUID {
        val modelId = putToCache(sparkModel)
        val model = Clustering.Model()
        model.id = modelId.toString()

        entity.models.add(model)

        transactionTemplate.execute {
            update(entity)
        }

        return modelId
    }

    override fun predict(entity: Clustering, jsonOrSql: String): Dataset<Row> {
        if (entity.models.isEmpty()) {
            throw IllegalStateException("No models have been built")
        }

        assert(aggregateService is AggregateServiceImpl)

        val model = entity.models.stream().findFirst().orElseThrow { EntityNotFoundException() }
        val sparkModel: Model<*> =
            when (entity.algorithm) {
                is BisectingKMeans -> getFromCache<BisectingKMeansModel>(UUID.fromString(model.id))
                is KMeans -> getFromCache<KMeansModel>(UUID.fromString(model.id))
                else -> throw UnsupportedOperationException()
            }

        val dataset = clusteringFunction.predict(sparkModel, jsonOrSql)

        datasetService.write(dataset, entity.sinks, false)

        return dataset
    }
}
package io.openenterprise.incite.ml.service

import io.openenterprise.ignite.cache.query.ml.ClassificationFunction
import io.openenterprise.incite.data.domain.Classification
import io.openenterprise.incite.data.domain.LogisticRegression
import io.openenterprise.incite.service.AggregateService
import io.openenterprise.incite.service.AggregateServiceImpl
import org.apache.spark.ml.Model
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import java.util.*
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Named
import javax.persistence.EntityNotFoundException

@Named
class ClassificationServiceImpl(
    @Inject private val aggregateService: AggregateService,
    @Inject private val classificationFunction: ClassificationFunction
) :
    ClassificationService,
    AbstractServiceImpl<Classification, String, ClassificationFunction>(aggregateService, classificationFunction) {

    override fun <M : Model<M>> buildModel(entity: Classification): M {
        val dataset = getAggregatedDataset(entity)

        @Suppress("UNCHECKED_CAST")
        return when (entity.algorithm) {
            is LogisticRegression -> {
                val logisticRegression = entity.algorithm as LogisticRegression

                classificationFunction.buildLogisticRegressionModel(
                    dataset,
                    if (logisticRegression.family == null) null else logisticRegression.family!!.name.lowercase(),
                    logisticRegression.featureColumns.stream().collect(Collectors.joining(",")),
                    logisticRegression.labelColumn,
                    logisticRegression.elasticNetMixing,
                    logisticRegression.maxIteration,
                    logisticRegression.regularization
                )
            }
            else ->
                throw UnsupportedOperationException()
        } as M
    }

    override fun predict(jsonOrSql: String, entity: Classification): Dataset<Row> {
        if (entity.models.isEmpty()) {
            throw IllegalStateException("No models have been built")
        }

        assert(aggregateService is AggregateServiceImpl)

        val model = entity.models.stream().findFirst().orElseThrow { EntityNotFoundException() }
        val sparkModel: Model<*> = when (entity.algorithm) {
            is LogisticRegression -> {
                getFromCache<LogisticRegressionModel>(UUID.fromString(model.id))
            }
            else ->
                throw UnsupportedOperationException()
        }

        val dataset = classificationFunction.predict(jsonOrSql, sparkModel)
        val aggregateServiceImpl = aggregateService as AggregateServiceImpl

        aggregateServiceImpl.writeSinks(dataset, entity.sinks, false)

        return dataset
    }
}
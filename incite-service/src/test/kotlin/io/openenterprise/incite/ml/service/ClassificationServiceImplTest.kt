package io.openenterprise.incite.ml.service

import com.google.common.collect.Sets
import io.openenterprise.incite.data.domain.*
import io.openenterprise.incite.data.repository.ClassificationRepository
import io.openenterprise.incite.service.PipelineService
import io.openenterprise.incite.spark.sql.service.DatasetService
import org.apache.commons.lang3.RandomStringUtils
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.assertj.core.util.Lists
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*

@Ignore
@RunWith(SpringRunner::class)
@Import(AbstractMachineLearningServiceImplTest.Configuration::class)
class ClassificationServiceImplTest : AbstractMachineLearningServiceImplTest() {

    private val classification = Classification()

    @Autowired
    private lateinit var classificationRepository: ClassificationRepository

    @Autowired
    private lateinit var classificationService: ClassificationService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Before
    fun before() {
        val rdbmsDatabase = rdbmsDatabase()

        val jdbcSource = JdbcSource()
        jdbcSource.query = "select g.id, g.age, g.sex, g.result from guest g"
        jdbcSource.rdbmsDatabase = rdbmsDatabase

        classification.id = UUID.randomUUID().toString()
        classification.sources = Lists.list(jdbcSource)

        jdbcTemplate.update(
            "create table if not exists guest (id bigint primary key, membership_number varchar, age smallint, " +
                    "sex smallint, result smallint, created_date_time timestamp with time zone, last_login_date_time timestamp with time zone)"
        )
        jdbcTemplate.update("insert into guest values (1, '${RandomStringUtils.randomNumeric(9)}', 35, 0, 3, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (2, '${RandomStringUtils.randomNumeric(9)}', 18, 1, 2, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (3, '${RandomStringUtils.randomNumeric(9)}', 20, 1, 2, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (4, '${RandomStringUtils.randomNumeric(9)}', 40, 1, 4, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (5, '${RandomStringUtils.randomNumeric(9)}', 65, 1, 5, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (6, '${RandomStringUtils.randomNumeric(9)}', 33, 0, 3, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (7, '${RandomStringUtils.randomNumeric(9)}', 16, 0, 1, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (8, '${RandomStringUtils.randomNumeric(9)}', 25, 0, 2, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (9, '${RandomStringUtils.randomNumeric(9)}', 9, 1, 1, now(), now()) on conflict do nothing")
        jdbcTemplate.update("insert into guest values (10, '${RandomStringUtils.randomNumeric(9)}', 46, 1, 5, now(), now()) on conflict do nothing")

        Mockito.`when`(classificationService.retrieve(classification.id.toString())).thenReturn(classification)
    }

    @Test
    fun testSetUp() {
        val algo = Classification.SupportedAlgorithm.LOGISTIC_REGRESSION.name
        val algoSpecificParams = "{\"featureColumns\": [\"age\", \"sex\"], \"labelColumn\": \"result\", \"maxIterations\": 10}"
        val sourceSql = "select g.id, g.age, g.sex, g.result from guest g"
        val sinkTable = "test_set_up_classification"
        val primaryKeyColumn = "id"

        Mockito.`when`(classificationRepository.save(Mockito.any())).thenAnswer {
            (it.arguments[0] as Classification).id = UUID.randomUUID().toString()

            it.arguments[0]
        }

        val uuid = ClassificationService.setUp(
            algo,
            algoSpecificParams,
            sourceSql,
            sinkTable,
            primaryKeyColumn
        )

        assertNotNull(uuid)
    }

    @Test
    fun testTrainLogisticRegressionModel() {
        val algorithm = LogisticRegression()
        algorithm.labelColumn = "result"
        algorithm.featureColumns = Sets.newHashSet("age", "sex")

        classification.algorithm = algorithm

        val logisticRegressionModel: LogisticRegressionModel = classificationService.train(classification)

        assertNotNull(logisticRegressionModel)
        assertTrue(logisticRegressionModel.hasSummary())
    }

    @TestConfiguration
    class Configuration {

        @Bean
        protected fun classificationRepository(): ClassificationRepository =
            Mockito.mock(ClassificationRepository::class.java)

        @Bean
        protected fun classificationService(
            datasetService: DatasetService,
            pipelineService: PipelineService
        ): ClassificationService =
            ClassificationServiceImpl(datasetService, pipelineService)
    }
}
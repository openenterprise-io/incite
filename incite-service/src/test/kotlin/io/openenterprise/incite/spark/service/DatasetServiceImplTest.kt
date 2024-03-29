package io.openenterprise.incite.spark.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.fasterxml.jackson.core.type.TypeReference
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import io.openenterprise.incite.data.domain.*
import io.openenterprise.incite.spark.sql.service.DatasetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.apache.commons.lang3.RandomStringUtils
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCluster
import org.apache.ignite.IgniteJdbcThinDriver
import org.apache.ignite.Ignition
import org.apache.ignite.cluster.ClusterState
import org.apache.ignite.configuration.CacheConfiguration
import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.configuration.SqlConfiguration
import org.apache.ignite.indexing.IndexingQueryEngineConfiguration
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.UUIDSerializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StringType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
class DatasetServiceImplTest {

    @Autowired
    private lateinit var amazonS3: AmazonS3

    @Autowired
    private lateinit var datasetService: DatasetService

    @Autowired
    private lateinit var ignite: Ignite

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var localStackContainer: LocalStackContainer

    @Autowired
    private lateinit var kafkaContainer: KafkaContainer

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<UUID, TestObject>

    @Autowired
    private lateinit var postgreSQLContainer: PostgreSQLContainer<*>

    @Before
    fun before() {
        amazonS3.createBucket(this::class.java.simpleName.lowercase())
        amazonS3.putObject(
            this::class.java.simpleName.lowercase(),
            "test_object.json",
            Paths.get("./src/test/resources/test_objects.json").toFile()
        )

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
    }

    @Test
    fun testLoadFromRdbms() {
        val rdbmsDatabase = RdbmsDatabase()
        rdbmsDatabase.driverClass = postgreSQLContainer.driverClassName
        rdbmsDatabase.url = postgreSQLContainer.jdbcUrl
        rdbmsDatabase.username = postgreSQLContainer.username
        rdbmsDatabase.password = postgreSQLContainer.password

        val jdbcSource = JdbcSource()
        jdbcSource.fields = Sets.newHashSet(
            Field("id"), Field("age"),
            Field("sex", "case when #field = 0 then 'F' else 'T' end as #field")
        )
        jdbcSource.query = "select g.id, g.age, g.sex, g.result from guest g"
        jdbcSource.rdbmsDatabase = rdbmsDatabase

        val dataset = datasetService.load(jdbcSource)

        assertNotNull(dataset)
        assertTrue(dataset.schema().fields().size == 3)
        assertTrue(Arrays.stream(dataset.schema().fields()).filter { it.name() == "sex" }
            .allMatch { it.dataType() is StringType })
    }

    @Test
    fun testLoadFromS3() {
        val fileSource = FileSource()
        fileSource.format = FileSource.Format.JSON
        fileSource.path = "s3a://${this::class.java.simpleName.lowercase()}/test_object.json"
        fileSource.streamingRead = false

        val dataset = datasetService.load(fileSource)

        assertEquals(1L, dataset.count())
    }

    @Test
    fun testStreamingWriteFromFile() {
        // val dataset = sparkSession.readStream().json("./src/test/resources/test_objects*.json")

        val fileSource = FileSource()
        fileSource.path = "./src/test/resources/test_objects*.json"

        val dataset = datasetService.load(fileSource)

        val rdbmsDatabase = RdbmsDatabase()
        rdbmsDatabase.driverClass = IgniteJdbcThinDriver::class.java.name
        rdbmsDatabase.url = "jdbc:ignite:thin://localhost:10800?lazy=true&queryEngine=h2"
        rdbmsDatabase.username = "ignite"
        rdbmsDatabase.password = "ignite"

        val igniteSink = IgniteSink()
        igniteSink.id = UUID.randomUUID().toString()
        igniteSink.primaryKeyColumns = "id"
        igniteSink.rdbmsDatabase = rdbmsDatabase
        igniteSink.table = "test_streaming_write_from_json_files"

        val streamingWrapper = StreamingWrapper(igniteSink)
        streamingWrapper.triggerType = StreamingSink.TriggerType.ProcessingTime
        streamingWrapper.triggerInterval = 1000L

        val datasetStreamingWriter = datasetService.write(dataset, streamingWrapper)

        assertNotNull(datasetStreamingWriter)
        assertNotNull(datasetStreamingWriter.streamingQuery)
        assertNotNull(datasetStreamingWriter.writer)

        Thread.sleep(20000)

        assertTrue(datasetStreamingWriter.streamingQuery.recentProgress().isNotEmpty())

        val igniteTableName = "SQL_PUBLIC_${igniteSink.table.uppercase()}"
        val igniteCache = ignite.cache<Any, Any>(igniteTableName)

        assertTrue(igniteCache.size() > 0)
    }

    @Test
    fun testStreamingWriteFromKafka() {
        val idField = Field("id")
        val field0Field = Field("field0")

        val kafkaCluster = KafkaCluster()
        kafkaCluster.servers = kafkaContainer.bootstrapServers

        val kafkaSource = KafkaSource()
        kafkaSource.fields = Sets.newHashSet(idField, field0Field)
        kafkaSource.kafkaCluster = kafkaCluster
        kafkaSource.startingOffset = KafkaSource.Offset.Earliest
        kafkaSource.topic = this.javaClass.simpleName

        val dataset = datasetService.load(kafkaSource)

        val rdbmsDatabase = RdbmsDatabase()
        rdbmsDatabase.driverClass = IgniteJdbcThinDriver::class.java.name
        rdbmsDatabase.url = "jdbc:ignite:thin://localhost:10800?lazy=true&queryEngine=h2"
        rdbmsDatabase.username = "ignite"
        rdbmsDatabase.password = "ignite"

        val igniteSink = IgniteSink()
        igniteSink.id = UUID.randomUUID().toString()
        igniteSink.primaryKeyColumns = "id"
        igniteSink.rdbmsDatabase = rdbmsDatabase
        igniteSink.table = "test_streaming_write_from_kafka"

        val streamingWrapper = StreamingWrapper(igniteSink)
        streamingWrapper.triggerType = StreamingSink.TriggerType.ProcessingTime
        streamingWrapper.triggerInterval = 1000L

        val datasetStreamingWriter = datasetService.write(dataset, streamingWrapper)

        kafkaTemplate.send(
            kafkaSource.topic,
            UUID.randomUUID(),
            TestObject(UUID.randomUUID().toString(), "Hello World!")
        )

        assertNotNull(datasetStreamingWriter)
        assertNotNull(datasetStreamingWriter.streamingQuery)
        assertNotNull(datasetStreamingWriter.writer)

        Thread.sleep(20000)

        assertTrue(datasetStreamingWriter.streamingQuery.recentProgress().isNotEmpty())

        val igniteTableName = "SQL_PUBLIC_${igniteSink.table.uppercase()}"
        val igniteCache = ignite.cache<Any, Any>(igniteTableName)

        assertTrue(igniteCache.size() > 0)
    }

    @TestConfiguration
    @ComponentScan(value = ["io.openenterprise.incite.spark.sql.service", "io.openenterprise.springframework.context"])
    class Configuration {

        @Bean
        protected fun amazonS3(localStackContainer: LocalStackContainer): AmazonS3 =
            AmazonS3ClientBuilder.standard()
                .withCredentials(localStackContainer.defaultCredentialsProvider)
                .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .build()

        @Bean
        protected fun coroutineScope(): CoroutineScope {
            return CoroutineScope(Dispatchers.Default)
        }

        @Bean
        protected fun dataSource(postgreSQLContainer: PostgreSQLContainer<*>): DataSource {
            val datasource = PGSimpleDataSource()
            datasource.setUrl(postgreSQLContainer.jdbcUrl)

            datasource.user = postgreSQLContainer.username
            datasource.password = postgreSQLContainer.password

            return datasource
        }

        @Bean
        protected fun ignite(applicationContext: ApplicationContext): Ignite {
            val cacheConfiguration = CacheConfiguration<Any, Any>()
            cacheConfiguration.name = "default"
            cacheConfiguration.isSqlEscapeAll = true

            val indexingQueryEngineConfiguration = IndexingQueryEngineConfiguration()
            indexingQueryEngineConfiguration.isDefault = true

            val sqlConfiguration = SqlConfiguration()
            sqlConfiguration.setQueryEnginesConfiguration(indexingQueryEngineConfiguration)

            val igniteConfiguration = IgniteConfiguration()
            igniteConfiguration.igniteInstanceName = this::class.java.simpleName
            igniteConfiguration.sqlConfiguration = sqlConfiguration

            igniteConfiguration.setCacheConfiguration(cacheConfiguration)

            return Ignition.getOrStart(igniteConfiguration)
        }

        @Bean
        protected fun igniteCluster(ignite: Ignite): IgniteCluster {
            val igniteCluster = ignite.cluster()

            try {
                return igniteCluster
            } finally {
                igniteCluster.state(ClusterState.ACTIVE)
            }
        }


        @Bean
        protected fun jdbcTemplate(datasource: DataSource): JdbcTemplate = JdbcTemplate(datasource)

        @Bean
        protected fun kafkaContainer(): KafkaContainer {
            val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
            kafkaContainer.start()

            return kafkaContainer
        }

        @Bean
        protected fun kafkaTemplate(kafkaContainer: KafkaContainer): KafkaTemplate<UUID, TestObject> {
            val configurations = ImmutableMap.builder<String, Any>()
                .put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
                .build()
            val producerFactory = DefaultKafkaProducerFactory(
                configurations,
                UUIDSerializer(),
                JsonSerializer(object : TypeReference<TestObject>() {})
            )

            return KafkaTemplate(producerFactory)
        }

        @Bean
        protected fun localStackContainer(): LocalStackContainer {
            val localStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
                .withServices(LocalStackContainer.Service.S3)
            localStackContainer.start()

            return localStackContainer
        }


        @Bean
        protected fun postgreSQLContainer(): PostgreSQLContainer<*> {
            val postgreSQLContainer: PostgreSQLContainer<*> =
                PostgreSQLContainer<PostgreSQLContainer<*>>("postgres:latest")
            postgreSQLContainer.withPassword("test_password")
            postgreSQLContainer.withUsername("test_user")

            postgreSQLContainer.start()

            return postgreSQLContainer
        }

        @Bean
        protected fun sparkSession(localStackContainer: LocalStackContainer): SparkSession = SparkSession.builder()
            .appName(DatasetServiceImplTest::class.java.simpleName)
            .master("local[*]")
            .config(
                "spark.hadoop.fs.s3a.endpoint",
                localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.S3).serviceEndpoint
            )
            .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
            .config("spark.hadoop.fs.s3a.path.style.access", true)
            .config("spark.hadoop.fs.s3a.access.key", localStackContainer.accessKey)
            .config("spark.hadoop.fs.s3a.secret.key", localStackContainer.secretKey)
            .config("spark.sql.streaming.schemaInference", true)
            .orCreate


        @Bean
        protected fun spelExpressionParser(): SpelExpressionParser {
            return SpelExpressionParser()
        }
    }

    class TestObject() {

        constructor(id: String, field0: String) : this() {
            this.id = id
            this.field0 = field0
        }

        lateinit var id: String

        lateinit var field0: String
    }
}
# Incite

## What is Incite?
Incite is a wrapper of popular data related libraries. It aims to provide easier access of these technologies for 
non-developers (i.e. Business analysis) in one organization.

It provides the following features,

* Data (streaming) aggregation
* Enterprise integration 
* Hybrid transaction/analytical processing (HTAP) SQL database
* Machine Learning (ML) with SQL query

## Data (streaming) aggregation
Data aggregation is to compile and combine data from different data-sources for different kind of data-processing. 
Incite provides RESTful APIs which allow users to define and run data aggregation against multiple origins (sources) 
and write the result of the aggregation to different destinations (sinks). Not only that, Incite supports streaming 
aggregation as it supports streaming read and streaming write from/to different data-sources.

#### Supported sources
* JDBC (Non-streaming)
* Kafka (Non-Streaming & Streaming)

#### Supported sinks
* Embedded Ignite (Non-streaming)
* Ignite (Non-streaming)
* JDBC (Non-streaming)
* Kafka (Non-streaming & Streaming)

### RESTful API

#### Create an aggregate definition

```text
POST    {{httpProtocol}}://{{host}}:{{port}}/rs/aggregates
```
###### Example
```text
curl --location --request POST 'http://localhost:8080/rs/aggregates' \
--header 'Content-Type: application/json' \
--data-raw '{
    "description": "Sample",
    "joins": [
        {
            "leftColumn": "membership_number",
            "rightColumn": "membership_number",
            "rightIndex": 1,
            "type": "INNER"
        }
    ],
    "fixedDelay": 0,
    "sinks": [
        {
            "@type": "EmbeddedIgniteSink",
            "id": "b26f1ff0-0e37-4be7-a4fe-911449f3dee4",
            "saveMode": "Append",
            "primaryKeyColumns": "transaction_id",
            "table": "guest_transactions"
        }
    ],
    "sources": [
        {
            "@type": "KafkaSource",
            "watermark": {
                "eventTimeColumn": "purchase_date_time",
                "delayThreshold": "5 minutes"
            },
            "streamingRead": true,
            "fields": [
                {
                    "function": "#field as transaction_id",
                    "name": "data.id"
                },
                {
                    "function": "#field as membership_number",
                    "name": "data.membership_number"
                },
                {
                    "function": "#field as sku",
                    "name": "data.sku"
                },
                {
                    "function": "#field as price",
                    "name": "data.price"
                },
                {
                    "function": "to_timestamp(#field, '\''yyyy-MM-dd HH:mm:ss.SSS'\'') as purchase_date_time",
                    "name": "data.created_date_time"
                }
            ],
            "kafkaCluster": {
                "servers": "PLAINTEXT://localhost:49346"
            },
            "startingOffset": "earliest",
            "topic": "transactions"
        },
        {
            "@type": "JdbcSource",
            "rdbmsDatabase": {
                "driverClass": "org.postgresql.Driver",
                "url": "jdbc:postgresql://localhost:49344/test?loggerLevel=OFF",
                "username": "test_user",
                "password": "test_password"
            },
            "query": "select g.id as guest_id, g.membership_number, g.created_date_time, g.last_login_date_time from guest g order by last_login_date_time desc"
        }
    ]
}'
```

#### Run a defined aggregate

```text
POST    {{httpProtocol}}://{{host}}:{{port}}/rs/aggregates/{{aggregateId}}/aggregate
```

## Enterprise integration
Enterprise integration allow data/message generated by different system to be integrated and processed to be further 
processed or to be shared. This functionality of Incite is powered by Apache Camel. Incite provides RESTful APIs which 
allow users to define an integration route written in Apache Camel's YAML DSL to ingest data from other systems and to 
egress the processed data to other systems or databases. 

### RESTful API

#### Create route

```text
POST    {{httpProtocol}}://{{host}}:{{port}}/rs/routes
```

Upon successful creation (i.e. API returns HTTP 201), the route will be started automatically.

###### Example
```text
curl --location --request POST 'http://localhost:8080/rs/routes' \
--header 'Content-Type: text/plain' \
--data-raw 'route:
    from: "ignite-messaging:sample_event_0?ignite='\''#{ignite}'\''"
    steps:
        - idempotent-consumer:
            expression: 
                header: "CamelIgniteMessagingUUID"
            message-id-repository-ref: "jdbcOrphanLockAwareIdempotentRepository"
        - unmarshal:
            json:
                library: Jackson
        - choice:
            when: 
                - expression:
                    spel: "#{request.body.type == '\''guest_complain'\''}"
                    steps:
                        - set-body:
                            simple: "insert into sample_event_0(id, guestId, content, eventType, isComplain, createdDateTime) values (UUID(), '\''${body.guestId}'\'', '\''${body.content}'\'', '\''${body.eventType}'\'', true, '\''${body.createdDateTime}'\'')"
            otherwise:
                steps:
                    - set-body:
                        simple: "insert into sample_event_0(id, guestId, content, eventType, isComplain, createdDateTime) values (UUID(), '\''${body.guestId}'\'', '\''${body.content}'\'', '\''${body.eventType}'\'', false, '\''${body.createdDateTime}'\'')"
        - to: "jdbc:igniteJdbcThinDataSource"'
```

## HTAP SQL database
Incite is powered by Apache Ignite which is also a hybrid transaction/analytical processing (HTAP) SQL database. 
This means that Incite is friendly to both analytical & transactional workload. One does not have to transfer data 
stored by transactional operations to a standalone data warehouse to be analysed which saves resources and time.

```properties
## Example SpringBoot configuration
spring.datasource.driver-class-name=org.apache.ignite.IgniteJdbcThinDriver
spring.datasource.url=jdbc:ignite:thin://${incite.host}:${incite.port}/incite?lazy=true
spring.datasource.username=${incite.username}
spring.datasource.password=${incite.password}
```

## ML with SQL query
Machine learning is a set of algorithms which provides insight of data. They can improve itself through the use of data.
Incite allows running machine learning algorithm against data stored on incite by using SQL query. Currently, Incite only
supports the following ML algorithms.

### 1. Cluster analysis
   * Bisecting k-means
   * K-means

#### Example:
```roomsql
-- Build a model for a ClusterAnalysis entity stored into Incite
select build_cluster_analysis_model('2036cd45-557e-41ce-a157-e64253295032');
-- Return the UUID of the built model

-- Perform prediction for the given ClusterAnalysis entity and given sql query
select cluster_analysis_predict('2036cd45-557e-41ce-a157-e64253295032', 'select * from sample_dataset');
-- Return the result in JSON format

-- Build a bisecting k-means model for an ad-hoc dataset 
-- buildBisectingKMeansModel(sql: String, featuresColumns: String, k: Int, maxIteration: Int, seed: Long)
select build_bisecting_k_means_model('select g.id, g.age, g.sex from guest g', 'age,sex', 4, 10, 1);
-- Return the UUID of the built model

-- Perform prediction with the bisecting k-means model
-- bisecting_k_means_predict(modelId: String, jsonOrSql: String)
select bisecting_k_means_predict('526d6e09-5c13-486f-951a-5dad58e3d36c', 'select nr.id, nr.age, nr.sex from newly_registered nr');
-- Return the result in JSON format
```
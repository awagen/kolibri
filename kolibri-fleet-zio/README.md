# kolibri-fleet-zio

## Getting an overview of effect composition in ZIO
The main concept to understand about ZIO is that every computation is constructed as descriptions
of the computation that need to be passed to a runtime to be executed.
As such it holds some similarities to frameworks such as Spark, where DAGs have to be run explicitly by an action method
on the dataframes.
And these descriptions are called effects, that are wrappers for any computation / function calls.
This is reflected in the main data type ZIO[R, E, A], where R stands for environment type (e.g environments provide
needed contextual data to execute the effect, such as DB connections; Any stands for no environment input needed to execute),
E stands for error type and A stands for the success type.

- Official doc: ```https://zio.dev/overview/getting-started```
- Example introducing some concepts step by step:
  ```https://scalac.io/blog/introduction-to-programming-with-zio-functional-effects/```

## Fibers and interrupting (https://zio.dev/reference/interruption/)
Reference to a fiber runtime can be created by calling one of the variants of ```.fork``` on a created
effect. A fiber provides an ```.interrupt``` method, which in general allows interrupting any fiber.
Yet that does not mean the computation (effect) wrapped into the ZIO.effect from which the fiber reference
was retrieved is also interrupted.
That is, if we call interrupt on a fiber that wraps a blocking operation such as ```Thread.sleep```,
the blocking operation will just go on (and make your tests based on assuming interruptibility take
a long time). Options allowing interruption of the underlying effect are:
- ```.attemptBlockingInterrupt```, such as in
```
fiber <- ZIO.attemptBlockingInterrupt({
  Thread.sleep(10000)
}).fork
```
This option comes with overhead, thus if performance is prio, this needs to be considered.
Also, it won't work on executions that catch and ignore ```InterruptedException```.
- ```.attemptBlockingCancelable```, such as in
```
case class CancellableTestWait() {
  private[this] val runFlag = new AtomicReference[Boolean](true)

  def run(): Unit = {
    while (runFlag.get()) {
      try Thread.sleep(5)
      catch {
        case _: InterruptedException =>
          println("Ignoring InterruptedException")
          ()
      }
    }
  }

  def stop(): Unit = {
    println("Stopping running task")
    runFlag.getAndSet(false)
    ()
  }
}
.....
for {
  service <- ZIO.attempt(CancellableTestWait())
  fiber <- ZIO.attemptBlockingCancelable(effect = {
    service.run()
  })(cancel = ZIO.succeed(service.stop()))
    .fork
} yield ()
```
This comes with the drawback that we have to implement the cancelling manually, such as
by changing an atomic reference value.


## Code coverage
To calculate code coverage, the sbt-scoverage plugin is used (https://github.com/scoverage/sbt-scoverage).
The commands to generate the reports are as follows:
- run tests with coverage: ```sbt clean coverage test``` (or in case project contains integration tests: ```sbt clean coverage it:test```)
- generate coverage report: ```sbt coverageReport``` (reports to be found in ```target/scala-<scala-version>/scoverage-report```)
- instead of running ```sbt coverageReport```, we can directly aggregate the results via ```sbt coverageAggregate```

Its also possible to enable coverage for each build via sbt setting ```coverageEnabled := true```.
For more settings (such as minimal coverage criteria for build to succeed), see above-referenced project page.


## Build jar, build docker image, startup local

- build jar (from root folder; find it in target folder of kolibri-fleet-zio sub-folder afterwards): ```./scripts/buildKolibriFleetZIOJar.sh```
- build docker image for local usage (kolibri-fleet-zio sub-folder): ```docker build . -t kolibri-fleet-zio:0.1.5```
- startup via docker-compose (kolibri-fleet-zio sub-folder): ```docker-compose up```
- to consider when starting up prometheus / response-juggler as defined in the docker-compose file (otherwise just comment out):
  - for prometheus to start up, create folder named ```data``` in prometheus folder before executing docker-compose up.
  - response-juggler is started up as an easy mock of a search system, to test the workflow or requesting,
    parsing and computation steps. The type of data returned is configured via environment variables.
    For details check the repo ```https://github.com/awagen/response-juggler```.
    Make sure that the config is set such that it contains the same product_ids that are used within
    the judgement file referenced in the job definition in case you want to test calculations of IR metrics.

## Notes on local execution with access to AWS account
One way to enable container access to AWS account (e.g as for writing results into S3 bucket or similar),
it is enough to mount the local directory where the credentials reside into the root .aws directory in the container,
by adding to the docker-compose below volume mount (assuming the aws credentials reside in the home folder on the host machine
within the .aws folder, which is the default location when calling ```aws configure``` (official docs: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html)):
```
volumes:
  - [path-to-home-on-host-machine]/.aws:/home/kolibri/.aws:ro
```
Now configure the AWS_PROFILE env var to any profile name you want to assume (and for which the above mounted folder contains
credentials). See example within docker-compose.yaml.
Note that if the configuration file is not in the default location ```~/.aws/config```, additionally the env variable
```AWS_CONFIG_FILE``` has to be set to the absolute location within the container where the config file was mounted to
(```e.g /home/kolibri/.aws/credentials ```).

In case you login via temporary token via sso, check aws doc how to retrieve respective credentials for programmatic
access (you can go to your sso web interface and select the account and then programmatic access).

## Notes on local execution with access to GCP account
Analogue to the description of enabling AWS access, which refers to a credentials folder,
for GCP we have to set the env variable ```GOOGLE_APPLICATION_CREDENTIALS``` to the full path
to the json service account key file (how to create a service account and respective key file
you can check in the google cloud documentation, its a matter of a few clicks).
To make it accessible within docker, you have to mount the directory containing the key file on your local machine
into the container:
```
volumes:
  - [path-to-dir-containing-key-file-on-local-machine]/:/home/kolibri/gcp:ro
```
and then setting env variable
```
GOOGLE_APPLICATION_CREDENTIALS: '/home/kolibri/gcp/[sa-key-file-name].json'
```
This enables the access for the app running within the docker container.
To utilize google storage for result reading / writing, a few more env variables are needed:
```
GCP_GS_BUCKET: [bucket name without gs:// prefix]
GCP_GS_PATH: [path from bucket root to append to all paths that are requested]
GCP_GS_PROJECT_ID: [the project id for which the used service account is defined and for which the gs bucket was created]
PERSISTENCE_MODE: 'GCP'
```
With those settings, execution results are stored and read from google storage bucket.


## Few notes on the use of TypeTaggedMap
In case TypeTaggedMap (such as implementation TypedMapStore) is used as type safe map, note that you might see error messages
indicating a type mismatch of the type that is assumed and the data parsed in case the parsing
is stored in a variable of generic type Seq[_], e.g where error message will include scala.collection.*.colon::colon as bit unclear description.
This refers to the unclear type of Seq[_], so you should parse to specific known types in those cases, e.g two samples where the first will yield
error and the second will be fine (the below assumes that the seqSelector selects a Seq[JsValue] from the JsValue input and casts the single
elements to type String, while seqSelector.classTyped is of type ClassTyped[Seq[String]]):
```
val value: Seq[_] = seqSelector.select(jsValue)
typedMap.put(seqSelector.classTyped, value)
```

```
val value: Seq[String] = seqSelector.select(jsValue).asInstanceOf[Seq[String]]
typedMap.put(seqSelector.classTyped, value)
```

## A few notes on play json lib vs spray json lib
In the project you will currently find both spray json lib for json parsing and the play json lib.
At the moment incoming requests (e.g job definitions in json format and so on) are handled by the spray lib,
while selectors in the parsed responses when requesting another service (e.g look at the job definitions for the
search optimization use case) are handled with the play lib. This has origin in better out of the box functionality of the
play lib when parsing elements from a json, single or recursive.
For spray there is an additional library providing this functionality (https://github.com/jrudolph/json-lenses),
which seems to even provide more functionality. For this sake you can expect the play json lib will be removed in
further iterations for the sake of only using spray or an alternative.

## Endpoints: CORS (see also: https://zio.dev/zio-http/examples/advanced/middleware-cors-handling/)
In the definition of the server endpoints (e.g object ServerEndpoints)
you will find an example cors config. Given such a config, any
endpoint can be made to apply this policy via suffixing it with ```@@ cors(corsConfig)```.

## Endpoints and example responses
- ```/health```: status endpoint. Returns some ignorable text. Important part here is the status code.
- ```/resources/global```: returns a list of resources currently loaded (such as judgement lists and the like) on a (limited to single nodes) global level, e.g 
without taking into account further assignments such as the job they are used for.
Example response:
```json
{
  "data": [
    {
      "resourceType": "JUDGEMENT_PROVIDER",
      "identifier": "ident1"
    }
  ],
  "errorMessage": ""
}
```

- ```/jobs/open```: Returns list of currently non-completed jobs.
Example response:
```json
{
  "data": [
    {
      "batchCountPerState": {
        "INPROGRESS_abc1": 5
      },
      "jobId": "taskSequenceTestJob2_1688830485073",
      "jobLevelDirectives": [
        {
          "type": "PROCESS"
        }
      ],
      "timePlacedInMillis": 1688830485073
    }
  ],
  "errorMessage": ""
}
```

- ```/jobs/batches```: Returns list of batches currently in process.
```json
{
  "data": [
    {
      "processingInfo": {
        "lastUpdate": "2023-07-09 00:56:22",
        "numItemsProcessed": 129,
        "numItemsTotal": 1000,
        "processingNode": "abc1",
        "processingStatus": "IN_PROGRESS"
      },
      "stateId": {
        "batchNr": 0,
        "jobId": "taskSequenceTestJob2_1688864117702"
      }
    },
    {
      "processingInfo": {
        "lastUpdate": "2023-07-09 00:56:22",
        "numItemsProcessed": 129,
        "numItemsTotal": 1000,
        "processingNode": "abc1",
        "processingStatus": "IN_PROGRESS"
      },
      "stateId": {
        "batchNr": 4,
        "jobId": "taskSequenceTestJob2_1688864117702"
      }
    },
    {
      "processingInfo": {
        "lastUpdate": "2023-07-09 00:56:22",
        "numItemsProcessed": 129,
        "numItemsTotal": 1000,
        "processingNode": "abc1",
        "processingStatus": "IN_PROGRESS"
      },
      "stateId": {
        "batchNr": 3,
        "jobId": "taskSequenceTestJob2_1688864117702"
      }
    },
    {
      "processingInfo": {
        "lastUpdate": "2023-07-09 00:56:22",
        "numItemsProcessed": 131,
        "numItemsTotal": 1000,
        "processingNode": "abc1",
        "processingStatus": "IN_PROGRESS"
      },
      "stateId": {
        "batchNr": 1,
        "jobId": "taskSequenceTestJob2_1688864117702"
      }
    },
    {
      "processingInfo": {
        "lastUpdate": "2023-07-09 00:56:22",
        "numItemsProcessed": 128,
        "numItemsTotal": 1000,
        "processingNode": "abc1",
        "processingStatus": "IN_PROGRESS"
      },
      "stateId": {
        "batchNr": 2,
        "jobId": "taskSequenceTestJob2_1688864117702"
      }
    }
  ],
  "errorMessage": ""
}
```


## License
The kolibri-fleet-zio code is licensed under APL 2.0.
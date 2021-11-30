[![Scala CI](https://github.com/awagen/kolibri-base/actions/workflows/scala.yml/badge.svg?event=push)](https://github.com/awagen/kolibri-base/actions/workflows/scala.yml)


# Kolibri Base

This project provides the mechanism to execute jobs based on akka, making use of clustering to distribute job batches.
![Alt text](images/kolibri.svg?raw=true "Kolibri Base")

Documentation link: https://awagen.github.io/kolibri/ \
Doc converging towards completion, getting there (check for updates).

## Tests
### Execution
- when executing in intellij, make sure to set env variable PROFILE=test
- when executed via sbt test, as defined in build.sbt, thie PROFILE env var is already set

### Writing tests with / without cluster startup

- the actor system for tests is started on per-test basis via letting the test extending KolibriTestKit (in case a
  cluster is needed for the test - despite being just 1-node cluster) or KolibriTestKitNoCluster (in case test needs no
  cluster at all)

## Notes on correct sender

- There are cases when sender() of a sent message is set to the default sender, which would be dead letter channel; one
  case where this happens is if a tell to some actor is made within an actor by calling an outside method that has no
  implicit actorRef: ActorRef attribute set. Here solutions are either to declare the implicit value on the external
  method (and propagating it down to where the tell is executed) or explicitly pass a sender and send it with the
  message, so that the other actor knows where to respond to.

## Troubleshoot

Connection refused:

- check ```ss -tnlp | grep :8558``` (e.g replace the port with the one used for contact probing (check discovery config/
  log messages for connection refused))
- in case firewall blocks something, might wanna check ```tcpdump -n icmp```

## Build jar, build docker image, startup local

- you might temporarily need to clone kolibri-datatypes and publish locally (see kolibri-datatypes README on instructions)
- build jar (find it in target folder afterwards): ```./scripts/buildJar.sh```
- build docker image for local usage: ```docker build . -t kolibri-base:0.1.0-rc0```
- run single-node cluster (compute and httpserver role, access via localhost:
  8000): ```./scripts/docker_run_single_node.sh```
    - sets interface of http server to 0.0.0.0 to allow requests from host system to localhost:8000 reach the service
      within the container
- start local 3-node cluster (one compute and httpserver node, two 'compute'-only nodes, access via localhost:
  8000): ```docker-compose up```
  - NOTE: starting response-juggler as used in the docker-compose.yaml requires cloning ```https://github.com/awagen/response-juggler``` and building
  the image locally via ```docker build -t response-juggler:0.1.0 .```. 
  This service provides a basic response fake to avoid having to have a real search system running, and the parameters defined in the docker-compose
  file contain the same product_ids that are used within the test-judgements.txt referenced in the example job definition.
  The response-juggler will respond to each request with a random
  sampling of the comma-separated PID_SAMPLEs, each result containing between MIN_PRODUCTS and MAX_PRODUCTS.
  If another response structure needed, this can easily be adjusted within the response-juggler 

## Example clustered job execution: pi-estimation with dart throws

A very simple example of a distributed job is the pi-calculation via dart throws, assuming circle bounded by square and
counting the propotion of throws landing in the circle. With increasing number of throws, the proportion should
approximate pi/4. Steps to execute this simple job example:

- build jar and docker image according to above calls
- start up cluster by ```docker-compose up``` command in project root
- example url call: ```http://localhost:8000/pi_calc?jobName=job1&nrThrows=10000000&batchSize=10000&resultDir=/app/data```
  Note that giving the volume mount ```./kolibri-test:/app/data``` as given in the docker-compose, this will write into
  the projects root's ```kolibri-test``` folder per default into the file ```dartThrowResult.txt```. Note that the job
  is set up as a simple example that has no boundary on the nr of throws processed per second, and all flowing into
  aggregator, thus if the aggregator is asked and the response waited for, this can lead to ask timeouts. This differs
  between jobs, and most jobs take more time than simple random number pick between 0 and 1, reducing the number of
  result messages. Yet given the way the aggregating actor is instantiated in the same instance as the
  RunnableExecutionActor executing the ActorRunnable (which produces the result messages), the message passing doesnt
  need serialization but is done via ref passing.
- kill job: ```http://localhost:8000/stopJob?jobId=job1```
- check status of job: ```http://localhost:8000/getJobStatus?jobId=job1```

## Example clustered job execution: grid evaluation search endpoint
Within the scripts subfolder you find a testSearchEval.json which provides the general outline of the job definition.
The script start_searcheval.sh fires this json to the correct endpoint, which then starts a grid evaluation on the given
parameters and writes an overall aggregation as well as per-query results. The results are written within the kolibri-test subfolder
in project root, and therein in subfolder equal to the job name given in the abovementioned json job definition.
Note that the json needs the connections to fire requests against to be defined, assuming service with name search-service
being added to docker-compose on port 80. Adjust to your individual endpoint(s).
Further, right now assumes responses corresponding to play json selector 
```"response" \ "docs" \\ "product_id"```, meaning structure at least containing the hierarchies: 
```{"response": {"docs": [{"product_id": ...}, {"product_id":...}], ...},...}```
This is changeable in the json job definition.

## Monitoring
Metrics are collected utilizing the open source lib Kamon and exposed for prometheus to scrape the metrics.
In the grafana subfolder you'll find an example dashboard for the kolibri system.
An overview of the represented metrics can be seen in below screenshot:
![Alt text](images/Kolibri-Dashboard-Grafana.png?raw=true "Kolibri Grafana Dashboard")

## Serialization

Within definitions of the jobs, care has to be taken to define all parts that are part of any message to be
serializable. The standard way for kolibri is the KolibriSerializable interface. Within the application.conf
section ```akka.actor.serializers```
the actual serializers are defined, while binding to specific classes is found
in ```akka.actor.serialization-bindings```. Here you will find that a serializer is bound to KolibriSerializable. Thus
on adding new definitions, make sure to make the parts extend this interface. Also, take into account that Lambda's are
not Serializable, so rather use the SerializableSupplier, SerializableFunction1 (function with one argument) and
SerializableFunction2 (function with two arguments) from the kolibri-datatypes library (or add new ones in case of more
arguments / other needs) or the respective Scala function interface, since scala functions extend Serializable. Avoid
using lambda's and java functions.

For testing purposes, within the test application.conf ```serialize-messages``` can be set true to serialize messages in
the tests even though they share the same jvm. ```serialize-creators``` does the same for Props (Props encapsulate the
info needed for an actor to create another one).

## Cluster singleton router

A cluster singleton router can be utilized via the below code snippet in case of config setting
startClusterSingletonRouter is set to true, which also causes the ClusterSingletonManager to be started at cluster
start.

```
val singletonProxyConfig: Config = ConfigFactory.load(config.SINGLETON_PROXY_CONFIG_PATH)
context.system.actorOf(
  ClusterSingletonProxy.props(
    singletonManagerPath = config.SINGLETON_MANAGER_PATH,
    settings = ClusterSingletonProxySettings.create(singletonProxyConfig)
  )
)
```

## Local docker-compose setup
The docker-compose.yml can be found in the project root. Following setup is provided:
- prometheus: http://localhost:9000
- grafana: http://localhost:3000
- 3 node Kolibri cluster, with metrics endpoint on http://localhost:9095/metrics (exposed by Kamon lib)

An example grid search evaluation can be performed as given in the ```scripts/start_searcheval.sh```. You will need 
to adjust paths where appropriate. The example fires requests to a search instance on the local machine, which is not provided here.

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
which seems to even provide more functionality. For this sake ayou can expect the play json lib will be removed in 
further iterations for the sake of only using spray.

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
```AWS_CONFIG_FILE``` has to be set to the directory within the container where the config file was mounted to.

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

## Status endpoints example outputs
- jobStates:
```
"
[
  {
    "jobId":"testJob",
    "jobType":"SearchEvaluation",
    "resultSummary":{"failedBatches":[],"nrOfBatchesSentForProcessing":2,"nrOfBatchesTotal":24,"nrOfResultsReceived":0,"result":"RUNNING"},
    "startTime":"2021-09-29T01:32:09.292+02:00[CET]"
  }
]
"
```

- /nodeState:
```
[
{
  "capacityInfo":
    {
      "cpuCapactiy":-1.0,
      "heapCapactiy":0.930558969033882,
      "loadCapacity":0.505,
      "mixCapacity":0.717779484516941},
      "cpuInfo":{
          "cpuCombined":-1.0,
          "cpuStolen":-1.0,
          "loadAvg":3.96,
          "nrProcessors":8
      },
     "heapInfo":{
        "heapCommited":1073741824,
        "heapMax":4294967296,
        "heapUsed":298246957
     },
     "host":"kolibri1",
     "port":8001
    },
{
  "capacityInfo":
    {
      "cpuCapactiy":-1.0,
      "heapCapactiy":0.9374729155097157,
      "loadCapacity":0.5925,
      "mixCapacity":0.7649864577548579},
      "cpuInfo":{
        "cpuCombined":-1.0,
        "cpuStolen":-1.0,
        "loadAvg":3.26,
        "nrProcessors":8
      },
      "heapInfo":{
        "heapCommited":1073741824,
        "heapMax":4294967296,
        "heapUsed":268551783
      },
      "host":"kolibri2",
      "port":8001
    }
]
```


## Code coverage 
To calculate code coverage, the sbt-scoverage plugin is used (https://github.com/scoverage/sbt-scoverage).
The commands to generate the reports are as follows:
- run tests with coverage: ```sbt clean coverage test``` (or in case project contains integration tests: ```sbt clean coverage it:test```)
- generate coverage report: ```sbt coverageReport``` (reports to be found in ```target/scala-<scala-version>/scoverage-report```)

Its also possible to enable coverage for each build via sbt setting ```coverageEnabled := true```.
For more settings (such as minimal coverage criteria for build to succeed), see above-referenced project page.

## Adding templates for execution definitions
Kolibri accepts posting job definitions to the server, which then is processed. In order to simplify the process
of defining such a definition, templates can be referenced from the storage (see config settings to see the distinct 
available storage types, e.g AWS, GCP, LOCAL,...). 
For this, relative to the base path configured, config property ```JOB_TEMPLATES_PATH``` defines where to look for folders.
Each folder corresponds to a distinct type. 
The routes to retrieve available template types and corresponding json templates are given in ```ResourceRoutes```.
Further, endpoint to store a new template of defined type is also defined here.
On calling with a specified type (given by the folder name), this name is normalized
(upper-cased and - replaced with _) and matched against values of ```TemplateTypeValidationAndExecutionInfo```.
If there is a match, the enum value's ```isValid``` call tries to match to the specific type the json shall correspond to
and its requestPath property defines against which endpoint the template can be thrown to actually execute it.
This route is used within route ```startExecutionDefinition```, which redirects a passed execution json to the
execution route for its particular type.
Note that within a specific template folder, an ```info.json``` can be placed, containing a json with keys corresponding to the valid
keys of the template structure and values giving descriptions of the possible values.
On retrieval of a specific template via the ```ResourceRoute's routes```, if available this info is loaded and passed under the key
```info``` in the response (the actual specific template is provided under key ```template```).

Thus, simplified to add a new template, you have to do:
- add a ```subfolder``` in the path given by ```JOB_TEMPLATES_PATH``` (which is relative to the configured base path)
- in ```ResourceRoutes.TemplateTypeValidationAndExecutionInfo```, define an enumeration value that equals to the folder's name 
after normalization (by default uppercase and - replaced with _; so folder search_evaluation corresponds to enum with name
SEARCH_EVALUATION)
- place the templates you want (and which should correspond to json representations of the defined template type) in there
with suffix ```.json```
- optionally place an ```info.json``` file in the folder, providing a json with keys being the possible template keys, 
and the values corresponding to descriptions of the values
- you can request available templates for given types via ```getJobTemplateOverviewForType``` route, and the content of
a template along with (if info.json is placed) some descriptions of the fields and values via the 
```getJobTemplateByTypeAndIdentifier``` route.

## Local execution - Issues and Fixes
- starting the setup as provided in docker-compose file can be resource intensive. You might experience within the
cluster heartbeat failures if not enough resources are available within docker. Thus make sure to allow sufficient 
resources (e.g >= 4gb ram) to avoid this. The mentioned heartbeat failures will lead to loss of inter-node connections.

## License

The kolibri-base code is licensed under APL 2.0.
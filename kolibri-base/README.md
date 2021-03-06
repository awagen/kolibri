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
- build jar (from root folder; find it in target folder of kolibri-base sub-folder afterwards): ```./scripts/buildKolibriBaseJar.sh```
- build docker image for local usage (kolibri-base sub-folder): ```docker build . -t kolibri-base:0.1.0-rc4```
- run single-node cluster (kolibri-base subfolder; compute and httpserver role, access via localhost:
  8000): ```./scripts/docker_run_single_node.sh```
    - sets interface of http server to 0.0.0.0 to allow requests from host system to localhost:8000 reach the service
      within the container
- start local 3-node cluster (kolibri-base subfolder; one compute and httpserver node, two 'compute'-only nodes, access via localhost:
  8000): ```docker-compose up```
  - NOTE: the docker-compose setup uses the discovery mode 'config' for specifying which nodes form a cluster. Thus
  for this example, the nodes are hard-coded in akka-discovery.conf. This is just the case for the example setup, 
  the 'production' setups such as the k8s example charts are configured such that the nodes find each other
  automatically whenever a new node is started up without specifying it specifically in any config (node-discovery).
  - NOTE: for prometheus to start up, create folder named ```data``` in prometheus folder before executing docker-compose up.
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

## Using distributed pubsub
Using pubsub mechanism in the cluster is easy.
- Create a pubsub mediator on cluster (one is enough): ```val mediator = DistributedPubSub.get(actorSystem).mediator```
- register actors you want to listen to a topic (use ```self``` as ActorRef if actor wants to register himself, or replace
it with any other ref):
```
mediator ! DistributedPubSubMediator.Subscribe("topic", self)
```
Further, make sure messages to be published under this topic are handled in the subscribed actor's receive method, 
otherwise there will only be increase in unhandled messages.
- publish a message on any node:
```
mediator ! DistributedPubSubMediator.Publish("topic", msg)
```

Note that the mediator will receive the message per node and then distribute it locally on each node.
Further, if subscribers are added after publish, they wont see the message.
Thus if you use this to keep some state updated, you either need to ensure all respective actors are already running
when publishing, or to publish more frequently (well, or distribute it in other ways, such as distributed data or
sending in messages, ...).

Check for already started mediator before creating one yourself, e.g ClusterNode object might already provide one.

## Notes on ClusterSharding
ClusterSharding can be activated with USE_SHARDING_AND_ENDPOINTS environment variable (see docker-compose.yml in kolibri-base).
This will start the ClusterSharding extension on each node and initialize it for actors that handle EventAggregatingActor.RequestEvent
messages (see ClusterNode object) and set some routes (see StateRoutes object) that allow posting of RequestEvent messages
with a given source. On posting a message, if within the cluster no actor for the sourceId passed in the message exists,
one is created, otherwise the new message is aggregated into the state of the already existing actor. This currently is rather
an example of usage, and activating this via USE_SHARDING_AND_ENDPOINTS should not affect anything other than adding this example use-case
accessible via the added routes.

Example curl: ```curl -XPOST localhost:8000/event/entity -H 'Content-Type: application/json' -d '{"sourceId": "b", "requestId": "q2", "event": "click", "targetEntityId": "p2"}'```shell

## Notes on balancing request settings
Currently each batch corresponds to an execution graph materialization. In case the task requires a connection pool
(as soon as ure requesting via akka-http that is the case and the provided job definitions do so), if you select many
concurrent tasks (meaning nr of batches) for the job, you might temporarily see a buffer overflow, indicating too many
concurrent requests.
This is due to balance of two settings ```akka.http.host-connection-pool.max-connections``` and 
```akka.http.host-connection-pool.max-open-requests```. That is, each flow has at most max-connections concurrent requests,
yet the pool's maximal nr of open requests is given by the max-open-requests settings.
Thus nr-of-batches-on-single-node * max-connections should not exceed max-open-requests, otherwise you'll see the 
overflow log messages.
So lets say youre requesting 10 tasks, have 3 compute nodes in the cluster those are distributed on, you should
make max-connections 1/4 of max-open-requests, since the most tasks any single node should have is 4 (this
could vary depending on the speed single batches are processed on the distinct nodes and the distribution mode selected, 
e.g round-robin could yield more than 4, but this would then be not in the initial stage of execution anymore but after some
batches are processed, in which case backpressure is likely already reducing the pull in the other materializations).
That being said, a distribution strategy avoiding this by e.g allowing definition of a max nr of tasks per node
will likely be included shortly.

## Endpoints for data file retrieval (e.g data prefefined within files)
This contains distinct kinds of parameter values. 
This can be preppred beforehand such that there is a selection of parameters 
to compose, e.g important queries or similar.
- ```curl "localhost:8000/data/filesByType?type=PARAMETER"```
- ```curl "localhost:8000/data/readFile?type=PARAMETER&identifier=test_queries.txt"```
- ```curl -XGET  "localhost:8000/generator/info?returnNSamples=10" --header "Content-Type: application/json" -d '{"type": "FROM_FILES_LINES_TYPE", "values": {"q": "inputdata/PARAMETER/test_queries.txt"}}'```
- ```curl -XGET  "localhost:8000/data/info/all?returnNSamples=10"```

## Endpoints for result retrieval
- Overview of executions for which results are available: ```http://localhost:8000/results/executions```
- Overview of single partial / aggregated results for any of the values returned by above endpoint: ```http://localhost:8000/results/executions/[executionId]```
- Full file overview: ```http://localhost:8000/results/executions/[executionId]/byId?id=[FILE_NAME]```
- Filtered file overview: ```http://localhost:8000/results/executions/[executionId]/byIdFiltered?id=[FILE_NAME]&sortByMetric=NDCG_10&topN=50&reversed=false```
The latter endpoint allows sorting by some given metric and filtering down to reduced set of results.

## Endpoints for result analysis
- analyze top/flop queries for given parameters: 
```curl -XPOST http://localhost:8000/analyze/topsflops -H 'Content-Type: application/json' -d '{"type": "ANALYZE_BEST_WORST_REGEX", "directory": "testJob1", "regex": ".*[(]q=.+[)]","currentParams": {"a1": ["0.45"], "k1": ["v1", "v2"], "k2": ["v3"], "o": ["479.0"]},"compareParams": [{"a1": ["0.32"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1760.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["384.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1325.0"]}], "metricName": "NDCG_10", "queryParamName": "q", "n_best": 10, "n_worst": 10}'```
- calculate variance of selected metric for grouping (e.g per query):
  ```curl -XPOST http://localhost:8000/analyze/variances -H 'Content-Type: application/json' -d '{"type": "ANALYZE_QUERY_METRIC_VARIANCE", "directory": "testJob1", "regex": ".*[(]q=.+[)]", "metricName": "NDCG_10", "queryParamName": "q"}'```


## Local execution - Issues and Fixes
- starting the setup as provided in docker-compose file can be resource intensive. You might experience within the
cluster heartbeat failures if not enough resources are available within docker. Thus make sure to allow sufficient 
resources (e.g >= 4gb ram) to avoid this. The mentioned heartbeat failures will lead to loss of inter-node connections.

## License

The kolibri-base code is licensed under APL 2.0.
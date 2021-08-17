[![Scala CI](https://github.com/awagen/kolibri-base/actions/workflows/scala.yml/badge.svg?event=push)](https://github.com/awagen/kolibri-base/actions/workflows/scala.yml)


# Kolibri Base

This project provides the mechanism to execute jobs based on akka, making use of clustering to distribute job batches.
![Alt text](images/kolibri.svg?raw=true "Kolibri Base")


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
- in case firewall blocks something, might wanna check ```sudo tcpdump -n icmp```

## Build jar, build docker image, startup local

- you might temporarily need to clone kolibri-datatypes and publish locally (see kolibri-datatypes README on instructions)
- build jar (find it in target folder afterwards): ```./scripts/buildJar.sh```
- build docker image for local usage: ```sudo docker build . -t kolibri-base:0.1.0-alpha5```
- run single-node cluster (compute and httpserver role, access via localhost:
  8000): ```./scripts/docker_run_single_node.sh```
    - sets interface of http server to 0.0.0.0 to allow requests from host system to localhost:8000 reach the service
      within the container
- start local 3-node cluster (one compute and httpserver node, two 'compute'-only nodes, access via localhost:
  8000): ```sudo docker-compose up```

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
This is changeable in the flow definition and will be added to the available configuration settings shortly.

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
´´´
val value: Seq[_] = seqSelector.select(jsValue)
typedMap.put(seqSelector.classTyped, value)
´´´

´´´
val value: Seq[String] = seqSelector.select(jsValue).asInstanceOf[Seq[String]]
typedMap.put(seqSelector.classTyped, value)
´´´

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

## License

The kolibri-base code is licensed under APL 2.0.
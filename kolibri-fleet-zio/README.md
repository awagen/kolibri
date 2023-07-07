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


## License
The kolibri-fleet-zio code is licensed under APL 2.0.
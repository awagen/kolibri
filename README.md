[![Scala CI](https://github.com/awagen/kolibri/actions/workflows/scala.yml/badge.svg?event=push)](https://github.com/awagen/kolibri/actions/workflows/scala.yml)

# Kolibri
The repository combines the distinct Kolibri projects.

![Alt text](images/kolibri.svg?raw=true "Kolibri")

## Kolibri DataTypes
Provides basic data structures used throughout Kolibri to simplify data
processing.

Documentation: <https://awagen.github.io/kolibri/kolibri-datatypes/>

## Kolibri Definitions
Contains functionality of composing ```what``` to execute, irrespective of the particular execution mechanism.
Provides job definitions regarding batch search evaluation / optimization, requesting the search system
and evaluating results based on judgement files and/or custom properties
of the responses of the search system.

## Kolibri Storage
Contains implementations for persistence, such as local as well as cloud-based readers / writers.

## Kolibri Fleet
The project was initially based on an Akka-based execution mechanism that allowed node clustering and 
collaborative execution. This mode is phasing out at the moment, making room for a more loosely coupled mechanism,
using the ZIO framework as processing engine, and based on storage-based definition of what needs to be done,
while all connected nodes then negotiate who executes what in a claim-based mechanism.
This opens nice, lean ways of quickly trying out stuff without the need to deploy: the mechanism does not differentiate
between deployed nodes or nodes connected from anywhere (such as your local machine or those of your colleagues), 
the only thing that matters is that those nodes have access to the persistence configured (such as an S3 bucket).

Note that the official documentation (<https://awagen.github.io/kolibri>) has not yet been adjusted but 
will be in the upcoming days.

Let's have a look at the available fleet implementations.

### Kolibri Fleet Zio
Provides api to post new jobs, retrieve node and processing status, provide results to the kolibri-watch UI 
and handle the executions. The usage is simple: you post a job definition (which all come with a clear definition
of needed fields and types that is interpreted in the kolibri-watch frontend to ease composition),
then mark it as ready for being processed. All connected nodes will then negotiate via claims who computes which
task. The nodes themselves to not communicate in any way except via state of the persistence.
That is, every node writes a node health file, claims for tasks it selected for execution, and a processing state
in case a claim was successful and processing has started.
Should a node go down, there will not be any updates anymore to the health status or the processing status.
All other connected nodes check for timeout on both types of updates and if exceeded claim the right to clean up
the state. This leads to resetting of tasks of the problematic node to open state (thus claimable by other nodes)
as well as removal of the node health file for the respective node. This way the state of the health files can
be understood as state of currently available nodes, irrespective of where they run.
They only need access to the used persistence (read and write). In case the defined jobs require access to any
system during processing, obviously the nodes also need access to those systems.

Currently two types of persistence can be used out of the box: local file storage and AWS S3 buckets.

Note that the current state reflects the transition from the old akka-based mode to the new claim-based mechanism
using the ZIO framework for the service implementation.
Thus the integration into kolibri-watch is pending, yet expected to be available in a few days.
The same holds for an example board for grafana such as in the example below for the akka-based project.


### Kolibri Fleet Akka ```Deprecated```
Provides cluster forming, webserver and worker nodes, and batch execution logic based on Akka.
It can both be run in single node mode as well as in a cluster formation, distinguishing between http server
and worker node roles (a single node can have both roles).

The project contains the same functionality as in the 0.1.5 release of kolibri-base, yet due to the split
into kolibri-definitions and kolibri-fleet-* projects the package structure has changed.
This sub-project is the result of this splitting of the former kolibri-base project and purely focusses on
akka-based job execution.
This sub-module will shortly be removed in favor of kolibri-fleet-zio. They both do understand the same job
definitions, so transitioning should be easy.

Documentation (Legacy): <https://awagen.github.io/kolibri/kolibri-base/>

Grafana Board:
![KolibriBase Grafana Board](images/Kolibri-Dashboard-Grafana.png?raw=true "KolibriBase Grafana Board")


## Kolibri Watch
Vue project providing a UI for Kolibri.
The UI allows to start batch executions based on templates and watch the process for jobs overall
and single batches in particular including resource consumption on the nodes.
Jobs can also be killed via UI.
Future iterations will also include result / analysis visualizations.

Documentation: <https://awagen.github.io/kolibri/kolibri-watch/>

Status overview of cluster:
![KolibriWatch Status](images/kolibri-watch-status.png?raw=true "KolibriWatch Status")

Creating job definitions from templates and starting jobs:
![KolibriWatch Templates](images/kolibri-watch-templates.png?raw=true "KolibriWatch Templates")

Finished job history:
![KolibriWatch History](images/kolibri-watch-finished-jobs.png?raw=true "KolibriWatch Finished Jobs")


## Subproject Handling
- executing sbt commands on single projects: include the project sub-path
in the command, such as ```sbt kolibri-definitions/compile```
- execute according to dependencies as defined in the root build.sbt, such as
compile in needed order ```sbt compile```


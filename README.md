[![Scala CI](https://github.com/awagen/kolibri/actions/workflows/scala.yml/badge.svg?event=push)](https://github.com/awagen/kolibri/actions/workflows/scala.yml)

# Kolibri
Kolibri is the tool for relevancy explorations of search systems and batch processing.
It implements a task queue that only needs the status information in a storage (currently local, AWS s3, GCP gcs are implemented).
The nodes that are used for submitting new jobs, picking status information (such as requested by kolibri-watch UI) just need to be 
able to connect to the defined storage (and request target systems in case this is part of the executed job). Who does what is then negotiated based on this data.
Thus whether you run it on your local, connect from your local to the cloud storage, connect with several colleagues to cloud storage from local,
deploy instances in the cloud or mix all of those does not matter.

**What you get - Relevancy Explorations**
- Not dependent on any target system client: as long as you can request the target system and get a json back,
  you are good.
- UI-supported composition of experiments
  - parameter permutations with full flexibility over altering url parameters, headers, bodies and effectively reduce the permutations
    in a grid search by mapping specific conditional lists of parameter values to other parameter values
    (such as mapping a specific query value to a list of filter values that is specific for that specific query).
  - metrics (such as well-known information retrieval (IR) metrics such as DCG, NDCG, ERR, Precision, Recall as well 
    as histograms of fields, jaccard comparison of results for distinct search systems and more)
  - judgement lists
  - batching (by any parameter)
  - tagging allows any granularity of generated results. Default is on a parameter level (such as query).
- UI-supported aggregation 
  - aggregate from folders by regex or by defining specific results to aggregate
  - use group-definitions to aggregate by group
  - use weight definitions to allow for a relative grading of sample-importance (such as weighting down queries that
    have a lower frequency)
- UI-supported creation and visualization of summaries
  - provides both an estimation of the effect the variation of the single parameters have on each calculated metric to quickly focus on the 
    right levers of result quality and representative parameter configurations for the group of "close to best" and "close to worst".
  - visualization of metric behavior on parameter changes


**What you get - Batch Processing**

Kolibri implements a storage based task queue mechanism that does not require setup of any queue or database.
It further provides a Task-sequence based composition of jobs, that can be used for arbitrary use-cases.
While relavancy explorations (see above) are the main use-case for the development of the UI and out-of-the-box
job definitions, it can be used as a general batch processor.


![Alt text](images/kolibri.svg?raw=true "Kolibri")

## Kolibri DataTypes
Provides basic data structures used throughout Kolibri to simplify data
processing.

Documentation: <https://awagen.github.io/kolibri_archive/kolibri-datatypes/>

## Kolibri Definitions
Contains functionality of composing ```what``` to execute, irrespective of the particular execution mechanism.
Provides job definitions regarding batch search evaluation / optimization, requesting the search system
and evaluating results based on judgement files and/or custom properties
of the responses of the search system.

## Kolibri Storage
Contains implementations for persistence, such as local as well as cloud-based readers / writers.

## Kolibri Fleet
The project was initially based on an Akka-based execution mechanism that allowed node clustering and 
collaborative execution. This has been phased out to make room for a more loosely coupled mechanism,
using the ZIO framework as processing engine, and based on storage-based definition of what needs to be done,
while all connected nodes then negotiate who executes what in a claim-based mechanism.
For the last state including the akka-mode, refer to the release tag ```v0.1.5-fleet``` (the tag ```v0.1.5``` represents
the code base when the akk-based execution mode was still within the kolibri-base project before the split into 
```kolibri-definitions``` and ```kolibri-fleet-akka```, while the same tag with ```-fleet``` suffix represents the 
same functionality but split into above-mentioned sub-projects).
From tag ```v2.0.0``` on the kolibri-fleet-akka subproject is removed and the documentation on the official doc-website 
will move to a legacy-subfolder.
The new mode opens nice, lean ways of quickly trying out stuff without the need to deploy: the mechanism does not differentiate
between deployed nodes or nodes connected from anywhere (such as your local machine or those of your colleagues), 
the only thing that matters is that those nodes have access to the persistence configured (such as an S3 bucket).

Note that the updated official documentation can be found on <https://awagen.github.io/kolibri>.
You will find the legacy-documentation of the akka-based project for now under
<https://awagen.github.io/kolibri/kolibri_archive/>.

Let's have a look at kolibri-fleet-zio.

### Kolibri Fleet Zio
Provides api to post new jobs, retrieve node and processing status, provide results to the kolibri-watch UI 
and handle the executions. The usage is simple: you post a job definition (which all come with a clear definition
of needed fields and types that is interpreted in the kolibri-watch frontend to ease composition),
then mark it as ready for being processed. All connected nodes will then negotiate via claims who computes which
task. The nodes themselves to not communicate in any way except via state of the persistence.
That is, every node writes a node health file, claims for tasks it selected for execution, and a processing state
in case a claim was successful and processing has started.

![Kolibri Overview](docs_material/kolibri_overview.png?raw=true "Kolibri Overview")

Should a node go down, there will not be any updates anymore to the health status or the processing status.
All other connected nodes check for timeout on both types of updates and if exceeded claim the right to clean up
the state. This leads to resetting of tasks of the problematic node to open state (thus claimable by other nodes)
as well as removal of the node health file for the respective node. This way the state of the health files can
be understood as state of currently available nodes, irrespective of where they run.
They only need access to the used persistence (read and write). In case the defined jobs require access to any
system during processing, obviously the nodes also need access to those systems.

Currently three types of persistence can be used out of the box: local file storage, AWS S3 buckets and GCP gcs.

#### Notes on throughput
To tune the throughput, check the curves for in/out task-queue element flow
(see grafana board below). If you see fast production of in-flow elements,
and slower consumption (out-flow elements), then you might want to 
increase the setting of the env variable `NUM_AGGREGATORS_PER_BATCH`.
In case you see a slow producer, you might want to increase the setting
for `NON_BLOCKING_POOL_THREADS` (in general you should assign more of the
available threads to the async pool compared to the blocking pool).
You might also play with `MAX_PARALLEL_ITEMS_PER_BATCH` and
`CONNECTION_POOL_SIZE_MIN/MAX`.
If you see that at times the app overwhelms the requested target application,
you can restrict the maximal throughput via `MAX_BATCH_THROUGHPUT_PER_SECOND`.



## Kolibri Watch
Vue project providing a UI for Kolibri.
The UI allows to start batch executions based on templates and watch the process for jobs overall
and single batches in particular including resource consumption on the nodes.
Jobs can also be killed via UI.

Documentation: <https://awagen.github.io/kolibri_archive/kolibri-watch/>

Status overview of cluster:
![KolibriWatch Status](images/Status.png?raw=true "KolibriWatch Status")

Finished job history:
![KolibriWatch History](images/History.png?raw=true "KolibriWatch Finished Jobs")

There are two main options to specify the job to execute:

**1) Creating job definitions from forms defined by structural definitions provided by the kolibri-fleet-zio API.**

Without using a pre-fill of fields by some existing template:
![KolibriWatch Templates](images/Create_Form_Empty.png?raw=true "KolibriWatch Form1")

Using a pre-fill of fields by some existing template (here a small dummy example for a test job that only waits in each batch):
![KolibriWatch Templates](images/Create_Form_SmallExample.png?raw=true "KolibriWatch Form2")

A longer example:
![KolibriWatch Templates](images/Create_Form_FillIn_Template.png?raw=true "KolibriWatch Form3")


**2) Raw edit mode of existing templates**
![KolibriWatch Templates](images/Create_Raw_Template_Edit.png?raw=true "KolibriWatch Form4")


Jobs submitted via ```Run Template``` button will only be stored as open job and displayed on the ```Status``` page.
To start any processing, we still need to place a processing directive, which can be done via ```Start``` button 
besides the listed job. Note that processing can be stopped via ```Stop``` button when it is marked to be processed.
Further, the ```Delete``` option removes the whole job definition, effectively stopping any execution on it.
Note that there will be some delay between ```Stop / Delete``` and the node actually stopping the processing.

### Experiment Result Visualization
Kolibri-Watch allows visualization of experiment summary results.
This includes :

- overviews of representational configuration examples for the `best` setting group as well as
the `worst` settings group.

![KolibriWatch WinnerLooser](images/KolibriUIWinnerLooserConfigs.png?raw=true "KolibriWatch WinnerLooser Configs")

- estimation of effect of single parameter variations for the different result files. Currently this contains 
`maxMedianShift` (for each setting of the parameter of interest calculate median and calculate the difference between min and max observed) 
and `maxSingleResultShift` (here observe different settings of the parameter of interest by comparing pairs where all other parameters
are kept constant and only the parameter of interest varies. Calculate the difference max - min and take the max over all). 

![KolibriWatch ParameterEffect](images/KolibriUIParameterEffect.png?raw=true "KolibriWatch Parameter Effect")


- Further, it is possible to create charts for single results on demand. For now line charts and histogram charts are
provided. This is planned to be extended shortly.
The `<>` symbol between chars appears if charts can be merged into each other, displaying both in the same
window. Merging works for an arbitrary number of charts.

![KolibriWatch SingleResult](images/KolibriUISingleResult.png?raw=true "KolibriWatch Single Result")



### Kolibri Grafana Dashboard

![KolibriWatch GrafanaBoard1](images/KolibriGrafana1.png?raw=true "KolibriWatch GrafanaBoard1")

![KolibriWatch GrafanaBoard2](images/KolibriGrafana2.png?raw=true "KolibriWatch GrafanaBoard2")

![KolibriWatch GrafanaBoard3](images/KolibriGrafana3.png?raw=true "KolibriWatch GrafanaBoard3")


  
## Subproject Handling
- executing sbt commands on single projects: include the project sub-path
in the command, such as ```sbt kolibri-definitions/compile```
- execute according to dependencies as defined in the root build.sbt, such as
compile in needed order ```sbt compile```


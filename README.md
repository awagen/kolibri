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

Currently two types of persistence can be used out of the box: local file storage and AWS S3 buckets.



## Kolibri Watch
Vue project providing a UI for Kolibri.
The UI allows to start batch executions based on templates and watch the process for jobs overall
and single batches in particular including resource consumption on the nodes.
Jobs can also be killed via UI.
Future iterations will also include result / analysis visualizations.

Documentation: <https://awagen.github.io/kolibri/kolibri-watch/>

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

  
## Subproject Handling
- executing sbt commands on single projects: include the project sub-path
in the command, such as ```sbt kolibri-definitions/compile```
- execute according to dependencies as defined in the root build.sbt, such as
compile in needed order ```sbt compile```


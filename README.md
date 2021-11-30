[![Scala CI](https://github.com/awagen/kolibri/actions/workflows/scala.yml/badge.svg?event=push)](https://github.com/awagen/kolibri/actions/workflows/scala.yml)

# Kolibri
The repository combines the distinct Kolibri projects.

![Alt text](images/kolibri.svg?raw=true "Kolibri")

## Kolibri DataTypes
Provides basic data structures used throughout Kolibri to simplify data
processing.

Documentation: ```https://awagen.github.io/kolibri/kolibri-datatypes/```

## Kolibri Base
Provides cluster forming, webserver and worker nodes, and batch execution logic including
jobs regarding batch search evaluation / optimization, requesting the search system
and evaluating results based on judgement files and/or custom properties
of the responses of the search system.

Documentation: ```https://awagen.github.io/kolibri/kolibri-base/```

## Kolibri Watch
Vue project providing a UI for Kolibri.
The UI allows to start batch executions based on templates and watch the process for jobs overall
and single batches in particular including resource consumption on the nodes.
Jobs can also be killed via UI.
Future iterations will also include result / analysis visualizations.

## Subproject Handling
- executing sbt commands on single projects: include the project sub-path
in the command, such as ```sbt kolibri-base/compile```
- execute according to dependencies as defined in the root build.sbt, such as
compile in needed order ```sbt compile```


## Install dependencies
```npm install```

## Run dev server
```npm run dev```

## Bundle project for production
Prepares resources to be served in dist folder.
```npm run build```

## Run dockerized
- example: ```https://vuejs.org/v2/cookbook/dockerize-vuejs-app.html```, ```https://cli.vuejs.org/guide/deployment.html#docker-nginx```
- ```docker build -t kolibri-watch:0.1.0 .```
- ```docker run -p 8080:80 --rm --name kolibri-watch-1 kolibri-watch:0.1.0``` (within container, nginx listens on port 80)
- or for non-detached: ```docker run -it -p 8080:80 --rm --name kolibri-watch-1 kolibri-watch:0.1.0```
- access app on ```localhost:8080```

## Env var management
- vite is exposing env variables via import.meta.env (https://vitejs.dev/guide/env-and-mode.html#env-files)
- there is priority assigned depending on which mode the app is started. Env variables that already exist when
vue client is executed should have highest prio and not be overwritten

## Example state commits for result retrieval and analysis result retrieval
- Getting executionIds for which results exist: 
```store.commit("updateAvailableResultExecutionIDs")```
- Getting single resultIds for an executionId
```store.commit("updateAvailableResultsForExecutionID", "testJob1")```
- Retrieve full result data for resultId for given executionId:
```store.commit("updateSingleResultState", {"executionId": "testJob1", "resultId": "(ALL1)"})```
- Retrieve filtered result data for resultId for given executionId:
```store.commit("updateSingleResultStateFiltered", {"executionId": "testJob1", "resultId": "(ALL1)",
    "metricName": "NDCG_10", "topN": 20, "reversed": false})
```
- Retrieve tops and flops queries (improving / worsening) for given executionId and current parameter settings and 
settings to compare against:
```
store.commit("updateAnalysisTopFlop", {
    "executionId": "testJob1",
    "currentParams": {"a1": ["0.45"], "k1": ["v1", "v2"], "k2": ["v3"], "o": ["479.0"]},
    "compareParams": [{"a1": ["0.32"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1760.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["384.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1325.0"]}],
    "metricName": "NDCG_10",
    "queryParamName": "q",
    "n_best": 10,
    "n_worst": 10})
```
- Retrieve per-query variance over all parameter variations, ordered decreasing by variance:
```
store.commit("updateAnalysisVariance", {
    "executionId": "testJob1",
    "metricName": "NDCG_10",
    "queryParamName": "q"})
```


## Setting up vitest ui
As test ui, the official vitest ui can be used (https://vitest.dev/guide/ui.html).
Also see here for quick overview: https://www.the-koi.com/projects/taking-a-closer-look-at-the-vitest-ui/.

## Debugging with vitest in webstorm / intellij
To set breakpoints in webstorm/intellij and run tests, create a node.js 
run configuration as described in the following link:
https://vitest.dev/guide/debugging.html#intellij-idea.
In short, this contains creating the run configuration with following 
properties:
- Working directory: [/path/to/your-project-root]
- JavaScript file: ./node_modules/vitest/vitest.mjs
- Application parameters: run --threads false

You can then run this configuration in debug mode and breakpoints set in the 
IDE will be taken into account.
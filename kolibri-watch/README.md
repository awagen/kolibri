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

## Testing components

### Test involving generated html
In case we want to test the rendered part of a component, we need to make sure
the result is actually added to the DOM. 
Note that this is also needed if within some lifecycle hook the DOM
is directly accessed, e.g it needs to be available to the Component.
This does not seem to happen if 
vue test-utils mount function is called without ```attachTo``` property, such as in:
```javascript
const div = document.createElement('div')
document.body.appendChild(div)

const vueWrapper = mount(SomeComponent, {
    props: {
        someProb: 'someValue'
            ...
    },
    attachTo: div
})
```

If we need the component being wrapped in another component,
we can use the parentComponent property in the mount call or 
by defining a wrapper component such as in the below
we can do this with
```javascript
function wrapperComponent(props) {
    return {
        template: `<NestedFieldSeqStructDef/>`,
        components: {
            NestedFieldSeqStructDef
        }
    }
}
let wrapComponent = mount(wrapperComponent(props), {
        // @ts-ignore
        props: props,
        attachTo: div
    })
let component = wrapComponent.findComponent(NestedFieldSeqStructDef)
```

Note that in case a wrapper component is used, the setProps needs to
be called against that one instead of the actual component
selected by the ```findComponent``` call.


Alternatively from testing-library/vue we can use the render function:
```javascript
const renderResult = render(SomeComponent, {
    props: {
        someProb: 'someValue'
            ...
    }
})
```

Note that both options differ in the way html elements are queried.
In the former:
```javascript
let inputContent = component.find(`#${inputId}`).element
```

In the latter: 
```javascript
let inputElement = await renderResult.container.querySelector(`#${inputId}`)
```
if you refer to an input element, inputElement.value will give you the currently
set value.


### Making sure changes are applied

To avoid testing for results before async methods / lifecycle hooks are 
completed, the command ```await flushPromises();``` (from vue/test-utils) can be used.
Afterwards using a html selector on expected elements should work, even
if the existence of the element depends on hooks such as onMounted or the like.

### Fixing failed source mapping
When setting breakpoint in the IDE, you might get a warning that source could not be mapped.
In this case add the generation of source map to the vite config, e.g within defineConfig in vite.config.js:
```
build: {
    sourcemap: true
}
```
After building it should work.

### Fixing heap size issues when building
- increase allowed heap size: ```export NODE_OPTIONS=--max_old_space_size=4096```
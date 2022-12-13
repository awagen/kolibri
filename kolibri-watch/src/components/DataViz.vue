<template>

  <div class="row-container columns">

    <!-- file selector -->
    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-type-1">Select Execution / Experiment</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="experimentSelectEvent($event)" class="form-select k-value-selector" id="template-type-1">
            <option>Choose an option</option>
            <option v-for="executionId in this.$store.state.resultState.availableResultExecutionIDs">{{
                executionId
              }}
            </option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Result</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="resultSelectEvent($event, this.$store.state.resultState.currentlySelectedExecutionID)"
                  class="form-select k-field k-value-selector"
                  id="template-name-1">
            <option>Choose an option</option>
            <option v-for="resultId in this.$store.state.resultState.availableResultsForSelectedExecutionID">{{
                resultId
              }}
            </option>
          </select>
        </div>

        <div class="k-form-separator"></div>
        <!-- select the metric name based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-metric-1">Select Metric</label>
        </div>
        <div class="col-9 col-sm-12">
          <select v-model="this.$store.state.resultState.selectedMetricName" @change="metricSelectEvent($event)"
                  class="form-select k-field k-value-selector"
                  id="template-metric-1">
            <option>Choose an option</option>
            <option v-for="metricName in this.$store.state.resultState.availableMetricNames">{{ metricName }}</option>
          </select>
        </div>


        <div class="k-form-separator"></div>
        <!-- button to add graphic to display -->
        <div class="col-9 col-sm-12">
        </div>
        <div class="col-3 col-sm-12 k-action-buttons">
          <button type='button' @click="addGraph()" class="k-form k-full btn btn-action"
                  id="add-data-1">
            ADD DATA
          </button>
        </div>
      </div>
    </form>

    <template :key="displayKey" v-for="(value, index) in data">
      <form class="form-horizontal col-12 column">
        <div class="form-group">
          <div class="col-12 col-sm-12">

            <!-- if there is a graph before, offer to merge them -->
            <div v-if="index > 0 && haveSameType(index, index - 1)" class="k-merge-button-container">
              <button type="button" @click.prevent="collapseData(index, index - 1)" class="btn btn-action k-merge-button s-circle">
                <i class="icon icon-resize-vert"></i>
              </button>
            </div>

            <div class="k-delete-button">
              <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
                 aria-label="Close" role="button"></a>
            </div>
            <ChartJsGraph
                :labels="value['labels']"
                :datasets="value['datasets']"
                :canvas-id="'canvas-' + index"
                chart-type="line"
                :index="index"
            />
          </div>
        </div>
      </form>
    </template>

  </div>

</template>

<script>
import Plotly from 'plotly.js-dist-min'
import ChartJsGraph from "../components/partials/ChartJsGraph.vue";
import {Chart, registerables} from "chart.js";
import {Colors} from "chart.js"

Chart.register(...registerables);
Chart.register(Colors);
import {onMounted, ref} from "vue";
import _ from "lodash";
import {useStore} from "vuex";

export default {

  components: {ChartJsGraph},
  props: [],
  methods: {
    experimentSelectEvent(event) {
      this.$store.commit("updateAvailableResultsForExecutionID", event.target.value)
    },

    resultSelectEvent(event, executionId) {
      let resultID = event.target.value
      let payload = {"executionId": executionId, "resultId": resultID}
      this.$store.commit("updateSingleResultState", payload)
    },

    metricSelectEvent(event) {
      let metricName = event.target.value
      this.$store.commit("updateSelectedMetricName", metricName)
    }


    /**
     structure of single results requested from server:

     * {
     *   data: [
     *     datasets: [
     *       {
     *         name: "n"
     *         data: [0.64, ...],
     *         failReasons: [{},..],
     *         failSamples: [0,..],
     *         successSamples: [1,..],
     *         weightedFailSamples: [0.0,...],
     *         weightedSuccessSamples: [1.0, ...]
     *       }
     *     ]
     *     // entry type indicates the kind of value, e.g avg (double / float) or histogram
     *     entryType: "DOUBLE_AVG" / ""NESTED_MAP_UNWEIGHTED_SUM_VALUE""
     *     failCount: 0
     *     successCount: 0
     *     labels: ["a", ...]  //all arrays in single datasets have as many elements as labels here
     *   ]
     *
     * }
     */
  },
  setup(props, context) {

    const store = useStore()

    let data = ref([])
    let displayKey = ref(0)

    /**
     * selected data should contain the following:
     * {
     *   dataType: "",
     *   labels: ["a", ...],
     *   name: "n",
     *   data: [0.64]
     * }
     * Based on this we can distinguish whether to add a plot of single points or
     * are deriving a histogram (histogram needs further selection,
     * e.g the single values the parameter can take. Then needs mapping of the
     * value keys to integers to pick the labels
     */
    function getDataForCurrentSelection() {
      // set metric name
      let metricName = store.state.resultState.selectedMetricName
      // extract data points and corresponding labels
      let dataType = store.state.resultState.metricNameToDataType[metricName]
      let data = []
      let labels = []
      store.state.resultState.fullResultForExecutionIDAndResultID.data.find(entries => {
        let relatedDataset = entries.datasets.find(dataset => dataset.name === metricName)
        if (relatedDataset !== undefined) {
          labels = entries.labels
          data = {}
          // if its only a series of float values, just set data to the array of values
          if (dataType === "DOUBLE_AVG") {
            // now by default sort by descending. For this zip labels and data
            // sort and then unzip
            let dataAndLabels = []
            for (const [index, label] of labels.entries()) {
              dataAndLabels.push([relatedDataset.data[index], label])
            }
            dataAndLabels = dataAndLabels.sort(function(a, b){ return b[0] - a[0]; })
            data = dataAndLabels.map(x => x[0])
            labels = dataAndLabels.map(x => x[1])
          }
          if (["NESTED_MAP_UNWEIGHTED_SUM_VALUE", "NESTED_MAP_WEIGHTED_SUM_VALUE"].includes(dataType)) {
            // for histogram data, we need per value a series of labels and a series of counts
            relatedDataset.data.forEach(sample => {
              let keys = Object.keys(sample)
              keys.forEach(key => {
                data[key] = {}
                let dataForKey = sample[key]
                // TODO: for other types of data it might no hold that the values the
                // counts are assigned to are numeric, thus add a more general mechanism
                // without numeric value casting
                // now will in the data and label keys
                let sortedKeys = Object.keys(dataForKey)
                let intPositionLabels = sortedKeys.map(x => parseInt(x))
                let positionCountValues = sortedKeys.map(x => dataForKey[x])
                data[key]["labels"] = intPositionLabels
                data[key]["data"] = positionCountValues
              })
            })
          }
          return true
        }
        return false
      })

      return {
        dataType: dataType,
        labels: _.cloneDeep(labels.map(x => {
          return Object.keys(x).flatMap(key => {
            return key + '__' + x[key].join('_')
          }).join('|')
        })),
        datasets: [
          {
            label: metricName,
            data: _.cloneDeep(data)
          }
        ]
      }
    }

    function haveSameType(index1, index2) {
      return data.value[index1].dataType === data.value[index2].dataType
    }

    /**
     * Option to collapse data from one index into another.
     * Both datasets need to share the same data type
     * @param startIndex
     * @param toIndex
     */
    function collapseData(startIndex, toIndex) {
      if (data.value.length <= Math.max(startIndex, toIndex)) {
        console.warn("Can not merge data for indices, at least one index out of bounds")
        return;
      }
      let data1 = data.value[startIndex]
      let data2 = data.value[toIndex]
      if (data1.dataType !== data2.dataType) {
        console.warn(`Can not merge datasets, datatypes differ: ${data1.dataType}, ${data2.dataType}`);
        return;
      }
      data2.datasets.push(...data1.datasets)
      deleteInputElement(startIndex)
    }

    function increaseDisplayKey() {
      displayKey.value = displayKey.value + 1
    }

    function addGraph() {
      let newData = getDataForCurrentSelection()
      data.value.push(newData)
    }

    function deleteInputElement(index) {
      let deletedData = data.value.splice(index, 1);
      increaseDisplayKey()
      return deletedData
    }

    onMounted(() => {
    })

    return {
      Colors,
      data,
      deleteInputElement,
      addGraph,
      displayKey,
      collapseData,
      haveSameType
    }
  }

}

</script>

<style scoped>

.row-container {
  margin: 3em;
}

.k-value-selector {
  color: black;
}

.k-form.btn {
  padding: 0;
  margin: 0;
  display: block;
  background-color: #9999;
  color: black;
  border-width: 0;
}

.k-full.btn {
  width: 98%;
  padding-right: 1em;
}

button#add-data-1 {
  background-color: darkgreen;
}

.k-action-buttons {
  margin-top: 0.5em;
}

.k-delete-button {
  float: right;
  display: inline-block;
  width: 2em;
  height: auto;
}

button.k-merge-button{
  background-color: #588274;
  border-width: 0;
  color: white;
}

.k-merge-button-container {
  text-align: center;
  margin-top: 2em;
  margin-bottom: 2em;
}




</style>
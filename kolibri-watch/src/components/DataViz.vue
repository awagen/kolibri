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

      </div>
    </form>

    <template v-for="(value, index) in labels">

      <form class="form-horizontal col-12 column">
        <div class="form-group">
          <div class="col-12 col-sm-12">
            <ChartJsGraph
                :labels="value"
                :datasets="datasets[index]"
                :canvas-id="String(index)"
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
import {onMounted} from "vue";
import dataVizTestJson from "../data/dataVizTestData.json";

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
    },


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

    let labels = [dataVizTestJson["labels"]]
    dataVizTestJson["datasets"].forEach(ds => {
      ds["name"] = ds["label"]
    })
    let datasets = [dataVizTestJson["datasets"]]

    onMounted(() => {})

    return {
      Colors,
      labels,
      datasets
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

</style>
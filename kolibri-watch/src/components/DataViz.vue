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
            <option v-for="executionId in this.$store.state.resultState.availableResultExecutionIDs">{{ executionId }}</option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Result</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="resultSelectEvent($event, this.$store.state.resultState.currentlySelectedExecutionID)" class="form-select k-field k-value-selector"
                  id="template-name-1">
            <option>Choose an option</option>
            <option v-for="resultId in this.$store.state.resultState.availableResultsForSelectedExecutionID">{{ resultId }}</option>
          </select>
        </div>

        <div class="k-form-separator"></div>
        <!-- select the metric name based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-metric-1">Select Metric</label>
        </div>
        <div class="col-9 col-sm-12">
          <select v-model="this.$store.state.resultState.selectedMetricName" @change="metricSelectEvent($event)" class="form-select k-field k-value-selector"
                  id="template-metric-1">
            <option>Choose an option</option>
            <option v-for="metricName in this.$store.state.resultState.availableMetricNames">{{ metricName }}</option>
          </select>
        </div>

      </div>
    </form>

    <form class="form-horizontal col-12 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="timelineChart"></canvas>
          </div>
        </div>
      </div>
    </form>
    <!-- second placeholder for chart  -->
    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="timelineChart1"></canvas>
          </div>
        </div>
      </div>
    </form>

    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="barChart1"></canvas>
          </div>
        </div>
      </div>
    </form>

    <!--    <div class="form-horizontal col-12">-->
    <!--      <form class="form-horizontal col-6 column">-->
    <!--        <h3 class="k-title">-->
    <!--          Visualization Selection-->
    <!--        </h3>-->
    <!--      </form>-->
    <!--    </div>-->
  </div>

</template>

<script>
import Plotly from 'plotly.js-dist-min'
import {Chart, registerables} from "chart.js";
import {Colors} from "chart.js"

Chart.register(...registerables);
Chart.register(Colors);
import {onMounted} from "vue";
import dataVizTestJson from "../data/dataVizTestData.json";
import {preservingJsonFormat} from "../utils/formatFunctions";
import {interpolateYlGn} from "d3-scale-chromatic";
import * as d3 from 'd3';;

export default {

  components: {},
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
    TODO: provide methods to select single data from
    this.$store.state.resultState.fullResultForExecutionIDAndResultID
    after executino and result id are selected
    structure:

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

    let numDatasets = dataVizTestJson["datasets"].length

    const data = {
      labels: dataVizTestJson["labels"],

      // for nice colors, see also:
      // https://medium.com/code-nebula/automatically-generate-chart-colors-with-chart-js-d3s-color-scales-f62e282b2b41
      // and
      // https://github.com/d3/d3-scale-chromatic
      datasets: dataVizTestJson["datasets"].map((data, index) => {
        let color = numDatasets > 1 ? d3.interpolateCool(index / (numDatasets - 1)) : interpolateYlGn(1.0)
        data["backgroundColor"] = color
        data["borderColor"] = color
        data["borderWidth"] = 1
        data["lineTension"] = 0.2
        data["fill"] = false
        return data
      })
    };

    console.info(`data: ${preservingJsonFormat(data)}`)

    const config = {
      type: 'line',
      data: data,
      options: {
        maintainAspectRatio: false,
        plugins: {
          colors: {
            enabled: false
          }
        }
      }
    };

    const barConfig1 = {
      type: 'bar',
      data: data,
      options: {
        maintainAspectRatio: false,
        plugins: {
          colors: {
            enabled: false
          }
        },
        indexAxis: 'x',
        scales: {
          xAxes: {
            ticks: {
              autoSkip: false,
              maxRotation: 90,
              minRotation: 90
            }
          }
        }
      }
    };

    onMounted(() => {
      let chartElement = document.getElementById('timelineChart')
      let chartElement1 = document.getElementById('timelineChart1')
      let barChartElement1 = document.getElementById('barChart1')

      const chart = new Chart(
          chartElement,
          config
      );
      const chart1 = new Chart(
          chartElement1,
          config
      );
      const barChart1 = new Chart(
          barChartElement1,
          barConfig1
      );
    })

    return {
      Colors
    }


  }


}

</script>

<style scoped>

.row-container {
  margin: 3em;
}

.timeSeriesContainer {
  height: 400px;
}

.k-value-selector {
  color: black;
}


</style>
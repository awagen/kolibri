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

        <!-- Optional fields that will only be selectable if the selected metric corresponds to a histogram -->
        <template v-if="isHistogramMetrics()">
          <div class="k-form-separator"></div>
          <!-- select the metric name based on above selection -->
          <div class="col-3 col-sm-12">
            <label class="form-label" for="template-histogram-label-1">Select Histogram Label</label>
          </div>
          <div class="col-9 col-sm-12">
            <select v-model="this.$store.state.resultState.selectedHistogramLabelName"
                    @change="histogramLabelSelectEvent($event)"
                    class="form-select k-field k-value-selector"
                    id="template-histogram-label-1">
              <option>Choose an option</option>
              <option v-for="labelName in this.$store.state.resultState.availableHistogramLabelNames">{{
                  labelName
                }}
              </option>
            </select>
          </div>
          <div class="k-form-separator"></div>
          <!-- select the histogram value for which to display a histogram -->
          <div class="col-3 col-sm-12">
            <label class="form-label" for="template-histogram-value-1">Select Histogram Value</label>
          </div>
          <div class="col-9 col-sm-12">
            <select v-model="this.$store.state.resultState.selectedHistogramValue"
                    @change="histogramValueSelectEvent($event)"
                    class="form-select k-field k-value-selector"
                    id="template-histogram-value-1">
              <option>Choose an option</option>
              <option v-for="histogramValue in this.$store.state.resultState.availableHistogramValues">{{
                  histogramValue
                }}
              </option>
            </select>
          </div>
        </template>

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

    <template :key="displayKey" v-for="(value, index) in this.$store.state.resultState.selectedData">
      <form class="form-horizontal col-12 column">
        <div class="form-group">
          <div class="col-12 col-sm-12">

            <!-- if there is a graph before, offer to merge them -->
            <div v-if="index > 0 && haveSameType(index, index - 1)" class="k-merge-button-container">
              <button type="button" @click.prevent="collapseData(index, index - 1)"
                      class="btn btn-action k-merge-button s-circle">
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
                :chart-type="dataToDisplayType(value)"
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
import _ from "lodash";
import {preservingJsonFormat} from "@/utils/formatFunctions";

export default {

  components: {ChartJsGraph},
  props: [],
  data() {
    return {
      displayKey: 0
    }
  },
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
      let isHistogramMetric = this.metricIsHistogramMetric(metricName)
      this.$store.commit("updateSelectedMetricName", metricName)
      // now fill availableHistogramLabelNames and availableHistogramValues
      if (isHistogramMetric) {
        let histogramData = this.getHistogramDataForMetric(metricName)
        let availableLabels = histogramData.labels
        let availableHistogramValues = Object.keys(histogramData.data["data"][0])
        this.$store.commit("updateAvailableHistogramLabelNames", availableLabels)
        this.$store.commit("updateAvailableHistogramValues", availableHistogramValues)
      }
    },

    /**
     * Update the currently selected label that is fixed by selection, to be able to select
     * a distinct value out of the value map to be able to display the histogram of that value
     * (for the selected label, where label = set of parameters that identify the setting)
     * @param event
     */
    histogramLabelSelectEvent(event) {
      let label = event.target.value
      this.$store.commit("updateSelectedHistogramMetricLabel", label)
    },

    /**
     * Update the value for the current label (read: parameter settings) for which to display
     * a histogram
     * @param event
     */
    histogramValueSelectEvent(event) {
      let value = event.target.value
      this.$store.commit("updateSelectedHistogramValue", value)
    },

    dataToDisplayType(data) {
      if (["DOUBLE_AVG"].includes(data.dataType)) {
        return "line"
      }
      if (["NESTED_MAP_UNWEIGHTED_SUM_VALUE", "NESTED_MAP_WEIGHTED_SUM_VALUE"].includes(data.dataType)) {
        return "bar"
      }
    },

    metricIsHistogramMetric(metricName) {
      let metricNamesWithTypeMapping = Object.keys(this.$store.state.resultState.metricNameToDataType)
      if (!metricNamesWithTypeMapping.includes(metricName)) {
        return false;
      }
      let type = this.$store.state.resultState.metricNameToDataType[metricName]
      return ["NESTED_MAP_UNWEIGHTED_SUM_VALUE", "NESTED_MAP_WEIGHTED_SUM_VALUE"].includes(type);
    },

    /**
     * Identify whether histogram metric type is selected.
     * Return true if its the case, otherwise false
     * @returns {boolean}
     */
    isHistogramMetrics() {
      let selectedMetricName = this.$store.state.resultState.selectedMetricName
      return this.metricIsHistogramMetric(selectedMetricName)
    },

    /**
     * Transform a Map<String, Array<String>> representing the parameter mapping to a string
     * representation
     * @param paramMap
     * @returns {*}
     */
    parameterMapsToString(paramMapList) {
      return paramMapList.map(x => {
        return Object.keys(x).flatMap(key => {
          return key + '__' + x[key].join('_')
        }).join('|')
      })
    },

    /**
     * For given metric name, return complete info for it:
     * {
     *   entryType: "",
     *   data: [],
     *   labels: [],
     *   failCount: 0,
     *   successCount: 1
     *
     * }
     * Note that labels will already have been normalized to their corresponding
     * string representation to avoid dealing with Map(string -> Array<string>) parameter maps
     * @param metricName
     * @returns {{entryType: *, data: unknown, failCount, successCount, labels}}
     */
    getHistogramDataForMetric(metricName) {
      let entryWithDataset = this.$store.state.resultState.fullResultForExecutionIDAndResultID.data.find(entries => {
        return entries.datasets.find(dataset => dataset.name === metricName) !== undefined
      })
      if (entryWithDataset === undefined) {
        return {}
      }
      let dataset = entryWithDataset.datasets.find(dataset => dataset.name === metricName)
      if (dataset === undefined) {
        return {}
      }
      return {
        entryType: entryWithDataset.entryType,
        data: dataset,
        labels: this.parameterMapsToString(entryWithDataset.labels),
        failCount: entryWithDataset.failCount,
        successCount: entryWithDataset.successCount
      }
    },

    /**
     * Given metric name and label, find the respective data sample.
     * We need to identify the index of the respective label (here corresponding
     * to the selected setting of parameters) to pick the right data point containing
     * the histogram, which is given as Map<String, Map<String, Double>>
     * @param metricName
     * @param label
     */
    getHistogramMapForMetricNameAndLabel(metricName, label) {
      let histogramData = this.getHistogramDataForMetric(metricName)
      let stringLabels = histogramData.labels
      let labelIndex = stringLabels.indexOf(label)
      if (labelIndex < 0) {
        return {}
      }
      return histogramData["data"]["data"][labelIndex]
    },

    /**
     * Given a histogram map, the value for which to extract the position histogram from
     * and the dataType, prepare a full entry as consumed by chart.js, meaning the structure:
     * {
     *   dataType: dataType,
     *   labels: ascSortedLabels,
     *   datasets: [{
     *     label: label,
     *     data: data
     *   }]
     * }
     * Thus in case we have several of these data samples,
     * we will just align them terms of their labels.
     * That is, if one dataset has datapoints for positions [0, 1, 3] and the other at [2, 4],
     * we need to adjust the labels to cover both datasets, that is labels = [0, 1, 2, 3, 4]
     * and each dataset has to get data imputed for labels it does not yet fill, e.g each with
     * a count value of 0
     *
     * @param histogramMap
     * @param value
     * @param dataType
     * @returns {{dataType, datasets: [{data: *[], label: string}], labels: number[]}}
     */
    getHistogramDataEntryForValue(histogramMap, value, dataType) {
      let mappings = histogramMap[value]
      // now we have mappings {position -> count, position -> count} in mappings, separate in labels and data
      let label = `hist-${value}`
      let ascSortedLabels = Object.keys(mappings).map(key => parseInt(key)).sort(function (a, b) {
        return a[0] - b[0];
      })
      let data = ascSortedLabels.map(x => mappings[String(x)])
      return {
        dataType: dataType,
        labels: ascSortedLabels,
        datasets: [{
          label: label,
          data: data
        }]
      }
    },

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
    getDataForCurrentSelection() {
      // set metric name
      let metricName = this.$store.state.resultState.selectedMetricName
      // extract data points and corresponding labels
      let dataType = this.$store.state.resultState.metricNameToDataType[metricName]
      let data = []
      let labels = []
      // only in case of histograms metricNames will actually contain more names than metricName
      let metricNames = []
      this.$store.state.resultState.fullResultForExecutionIDAndResultID.data.find(entries => {
        let relatedDataset = entries.datasets.find(dataset => dataset.name === metricName)
        if (relatedDataset !== undefined) {
          labels = entries.labels
          // if its only a series of float values, just set data to the array of values
          if (dataType === "DOUBLE_AVG") {
            // now by default sort by descending. For this zip labels and data
            // sort and then unzip
            metricNames.push(metricName)
            let dataAndLabels = []
            for (const [index, label] of labels.entries()) {
              dataAndLabels.push([relatedDataset.data[index], label])
            }
            dataAndLabels = dataAndLabels.sort(function (a, b) {
              return b[0] - a[0];
            })
            data.push(dataAndLabels.map(x => x[0]))
            labels = dataAndLabels.map(x => x[1])
          }
          if (["NESTED_MAP_UNWEIGHTED_SUM_VALUE", "NESTED_MAP_WEIGHTED_SUM_VALUE"].includes(dataType)) {
            let selectedHistogramParameterSetting = this.$store.state.resultState.selectedHistogramLabelName
            let selectedHistogramValue = this.$store.state.resultState.selectedHistogramValue
            // now select the appropriate dataset for the settings
            let histogramMap = this.getHistogramMapForMetricNameAndLabel(metricName, selectedHistogramParameterSetting)
            let selectedHistogramData = this.getHistogramDataEntryForValue(histogramMap, selectedHistogramValue, dataType)

            // TODO: add more naming here e.g which experiment, which label, which parameter
            metricNames.push(`${metricName}-${selectedHistogramValue}`)
            labels = selectedHistogramData.labels
            data.push(selectedHistogramData.datasets[0].data)
          }
          return true
        }
        return false
      })

      // collecting distinct metrics for the current labels and dataType
      let datasets = []
      for (const [index, label] of metricNames.entries()) {
        datasets.push({
          label: label,
          data: _.cloneDeep(data[index])
        })
      }

      return {
        dataType: dataType,
        labels: labels,
        datasets: datasets
      }
    },

    haveSameType(index1, index2) {
      return this.$store.state.resultState.selectedData[index1].dataType === this.$store.state.resultState.selectedData[index2].dataType
    },

    updateSelectedDataEvent(data) {
      this.$store.commit("updateSelectedData", data)
    },

    /**
     * Option to collapse data from one index into another.
     * Both datasets need to share the same data type
     * @param startIndex
     * @param toIndex
     */
    collapseData(startIndex, toIndex) {
      if (this.$store.state.resultState.selectedData.length <= Math.max(startIndex, toIndex)) {
        console.warn("Can not merge data for indices, at least one index out of bounds")
        return;
      }
      let data1 = this.$store.state.resultState.selectedData[startIndex]
      let data2 = this.$store.state.resultState.selectedData[toIndex]
      if (data1.dataType !== data2.dataType) {
        console.warn(`Can not merge datasets, datatypes differ: ${data1.dataType}, ${data2.dataType}`);
        return;
      }
      let newData = _.cloneDeep(this.$store.state.resultState.selectedData)
      newData[toIndex].datasets.push(..._.cloneDeep(data1.datasets))
      this.updateSelectedDataEvent(newData)
      this.deleteInputElement(startIndex)
    },

    deleteInputElement(index) {
      let newData = _.cloneDeep(this.$store.state.resultState.selectedData)
      let deletedData = newData.splice(index, 1);
      this.updateSelectedDataEvent(newData)
      this.increaseDisplayKey()
      return deletedData
    },

    increaseDisplayKey() {
      this.displayKey.value = this.displayKey.value + 1
    },

    addGraph() {
      let newData = this.getDataForCurrentSelection()
      let updatedState = _.cloneDeep(this.$store.state.resultState.selectedData)
      updatedState.push(newData)
      this.updateSelectedDataEvent(updatedState)
    }


    /**
     structure of single results requested from server:

     * {
     *   data: [{
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
     *   },...
     *   ]
     *
     */
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

button.k-merge-button {
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
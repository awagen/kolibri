<template>
  <div class="form-container-experiment-create columns">
    <form class="form-horizontal col-6 column">
      <h3 class="title">
        Analysis Selection
      </h3>

      <!-- dropdown button group -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-type-1">Select Execution / Experiment</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="experimentSelectEvent($event)" class="form-select k-value-selector" id="template-type-1">
            <option>Choose an option</option>
            <option v-for="executionId in this.$store.state.availableResultExecutionIDs">{{ executionId }}</option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Result</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="filteredResultSelectEvent($event, this.$store.state.currentlySelectedExecutionID)" class="form-select k-field k-value-selector"
                  id="template-name-1">
            <option>Choose an option</option>
            <option v-for="resultId in this.$store.state.availableResultsForSelectedExecutionID">{{ resultId }}</option>
          </select>
        </div>
      </div>

      <!-- add some display of tabular data -->
      <div class="form-group">
        <div class="col-9 col-sm-12">
          <table class="table">
            <thead>
            <tr>
              <th v-for="column in this.$store.state.reducedFilteredResultForExecutionIDAndResultID.columnNames">{{ column }}</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="row in this.$store.state.reducedFilteredResultForExecutionIDAndResultID.dataLinesAsColumns">
              <td v-for="column in row">{{ column }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </form>

    <!-- Right part of the display, used for visualizations -->
    <form class="form-horizontal col-6 column k-json-panel">
      <h3 class="title">
        Visualization
      </h3>
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <IdValueChart :idValueArray="this.$store.state.analysisVariances"></IdValueChart>
        </div>
      </div>
    </form>
  </div>
</template>

<script>
import {onMounted} from "vue";
import IdValueChart from "./partials/IdValueChart.vue";

export default {
  components: {IdValueChart},
  props: [],
  data() {
    return {
      chart: null
    };
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

    filteredResultSelectEvent(event, executionId) {
      let resultID = event.target.value
      let payload = {
        "executionId": executionId,
        "resultId": resultID,
        // TODO: make those hardcoded values selectable
        "metricName": "NDCG_10",
        "topN": 20,
        "reversed": false
      }
      this.$store.commit("updateSingleResultStateFiltered", payload)
      this.updateAnalysisTopFlop(executionId)
      this.updateAnalysisVariance(executionId)
    },

    updateAnalysisTopFlop(executionId) {
      let payload = {
        "executionId": executionId,
        // TODO: make those hardcoded values selectable
        "currentParams": {"a1": ["0.45"], "k1": ["v1", "v2"], "k2": ["v3"], "o": ["479.0"]},
        "compareParams": [{"a1": ["0.32"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1760.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["384.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1325.0"]}],
        "metricName": "NDCG_10",
        "queryParamName": "q",
        "n_best": 10,
        "n_worst": 10
      }
      this.$store.commit("updateAnalysisTopFlop", payload)
    },

    updateAnalysisVariance(executionId) {
      let payload = {
        "executionId": executionId,
        // TODO: make those hardcoded values selectable
        "metricName": "NDCG_10",
        "queryParamName": "q"
      }
      this.$store.commit("updateAnalysisVariance", payload)
    },

  },

  setup(props) {

    onMounted(() => {
    })

    return {}
  }
}

</script>

<style scoped>

.k-field {
  background-color: #d3d3d3;
}

.k-form-separator {
  height: 2.8em;
}

.form-container-experiment-create {
  margin-top: 3em;
}

.column {
  padding: .4rem 0 0;
}

.title {
  margin-bottom: 1em;
  text-align: center;
}

/* need some deep selectors here since otherwise code loaded in v-html directive doesnt get styled */
::v-deep(pre) {
  padding-left: 2em;
  padding-top: 0;
  margin: 5px;
  text-align: left;
}

::v-deep(.string) {
  color: green;
}

::v-deep(.number) {
  color: darkorange;
}

::v-deep(.boolean) {
  color: black;
}

::v-deep(.null) {
  color: magenta;
}

::v-deep(.key) {
  color: #9c9c9c;
}

.k-value-selector {
  color: black;
}

.popover button {
  background-color: #588274;
  border-width: 0;
}

.table {
  font-size: .6rem;
  margin-left: 2em;
}

select.form-select.k-value-selector {
  width: 95%;
}

</style>

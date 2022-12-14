<template>
  <div class="form-container-experiment-create columns">
    <div class="form-horizontal col-12">
      <form class="form-horizontal col-6 column">
        <h3 class="k-title">
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
              <option v-for="executionId in this.$store.state.resultState.availableResultExecutionIDs">{{ executionId }}</option>
            </select>
          </div>
          <div class="k-form-separator"></div>
          <!-- select the needed template based on above selection -->
          <div class="col-3 col-sm-12">
            <label class="form-label" for="template-name-1">Select Result</label>
          </div>
          <div class="col-9 col-sm-12">
            <select @change="updateTopFlopAndVarianceSelectEvent($event, this.$store.state.resultState.currentlySelectedExecutionID)" class="form-select k-field k-value-selector"
                    id="template-name-1">
              <option>Choose an option</option>
              <option v-for="resultId in this.$store.state.resultState.availableResultsForSelectedExecutionID">{{ resultId }}</option>
            </select>
          </div>
        </div>
      </form>
    </div>

      <!-- add some display of tabular data -->
    <form v-if="Object.entries(this.$store.state.resultState.reducedFilteredResultForExecutionIDAndResultID).length > 0" class="form-horizontal col-6 column k-card-column">
      <div class="k-card-wrapper">
        <h3 class="k-title">
          Value Table
        </h3>
        <div class="form-group">
          <div class="col-12 col-sm-12">
            <Table
                :column-headers="this.$store.state.resultState.reducedFilteredResultForExecutionIDAndResultID.columnNames"
                :data="this.$store.state.resultState.reducedFilteredResultForExecutionIDAndResultID.dataLinesAsColumns">
            </Table>
          </div>
        </div>
      </div>
    </form>


    <!-- Right part of the display, used for visualizations -->
    <form v-bind:style="{'display':varianceDisplay}" class="form-horizontal col-6 column k-json-panel">
      <div class="k-card-wrapper">
        <h3 class="k-title">
          Variances
        </h3>
        <div class="form-group">
          <div class="col-12 col-sm-12">
            <IdValueChart :idValueArray="this.$store.state.analysisState.analysisVariances"></IdValueChart>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>

<script>
import {onMounted} from "vue";
import IdValueChart from "./partials/IdValueChart.vue";
import Table from "./partials/Table.vue";

export default {
  components: {Table, IdValueChart},
  props: [],
  data() {
    return {
      chart: null,
      /* TODO: this flag is only here to set variance display active / inactive ... can remove as soon as single evaluations
      * are selected on demand or some default is generated on the fly */
      varianceDisplay: 'none'
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

    updateTopFlopAndVarianceSelectEvent(event, executionId) {
      this.updateAnalysisTopFlop(executionId)
      this.updateAnalysisVariance(executionId)
    },

    updateAnalysisTopFlop(executionId) {
      let payload = {
        "executionId": executionId,
        // TODO: make those hardcoded values selectable
        "currentParams": {"a1": ["0.45"], "k1": ["v1", "v2"], "k2": ["v3"], "o": ["479.0"]},
        "compareParams": [{"a1": ["0.32"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1760.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["384.0"]},{"a1": ["0.45"],"k1": ["v1", "v2"],"k2": ["v3"],"o": ["1325.0"]}],
        "metricName": "NDCG@8",
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
        "metricName": "NDCG@8",
        "queryParamName": "q"
      }
      this.$store.commit("updateAnalysisVariance", payload)
      this.varianceDisplay = 'inline-block'
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
  margin-top: 2em;
}

.column {
  padding: .4rem 0 0;
}

.column.k-card-column {
  padding-right: 0;
}

.k-title {
  margin-top: 0.5em;
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

select.form-select.k-value-selector {
  width: 95%;
}

/* TODO: adjust settings such that the layout with the cards looks fine
    and add sorting options
    */
.k-card-wrapper {
  font-size: .6rem;
  margin-left: 2em;
  border-style: dashed;
  border-width: 1px;
  float: left; /* needed to fit the width of content */
  width: 95%;
}

.k-json-panel .k-card-wrapper {
  height: 67em;
}

</style>

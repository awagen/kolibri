<template>

  <!-- Dropdown activate / deactivate -->
  <div class="dropdownControl" :id="getDropdownControlsElementId()">
    <span class="k-tableControls">Load Data</span>
    <i :class="['icon', menuDropDownActive ? 'icon-arrow-down' : 'icon-arrow-up']"
       @click.prevent="toggleMenuDropdown"></i>
  </div>
  <!-- dropdown content -->
  <div class="dropdownContainer">
    <div :id="getDropdownElementId()" :class="['dropdown', menuDropDownActive && 'active']">

      <ul class="menu">
        <li class="divider" data-content="DateID Selection"/>
        <!-- DateId selection -->
        <template v-for="(dateId, dateIndex) in [...[...Object.keys(dateIdToJobIdsWithResultSummariesMapping)].sort()].reverse()">
          <li class="menu-item">
            <label class="form-radio form-inline">
              <input type="radio"
                     :name="'dateId-select-' + dateIndex"
                     :value="dateId"
                     :checked="(selectedDateIDValue === dateId) ? '' : null"
                     @change="setSelectedDateId(dateId)"
              />
              <i class="form-icon"></i>
              {{ dateId }}
            </label>
          </li>
        </template>

        <li class="divider" data-content="JobId Selection"/>
        <!-- JobId selection -->
        <template v-for="(jobId, jobIndex) in dateIdToJobIdsWithResultSummariesMapping[selectedDateIDValue]">
          <li class="menu-item">
            <label class="form-radio form-inline">
              <input type="radio"
                     :name="'jobId-select-' + jobIndex"
                     :value="jobId"
                     :checked="(selectedJobIdValue === jobId) ? '' : null"
                     @change="setSelectedJobId(jobId)"
              />
              <i class="form-icon"></i>
              {{ jobId }}
            </label>
          </li>
        </template>

        <li class="divider" data-content="Action"/>
        <!-- summary load button -->
        <template v-if="selectedJobIdValue !== '' && selectedDateIDValue !== ''">
          <li class="menu-item">

            <!-- change to button -->
            <Button
                class="k-dropdown-button"
                emitted-event-name="loadSummaryData"
                :emitted-event-arguments="{dateId: selectedDateIDValue, jobId: selectedJobIdValue}"
                @load-summary-data="loadSummaryData"
                button-class="start"
                button-shape="rectangle"
                title="Load Data"
            />

          </li>
        </template>

      </ul>
    </div>
  </div>


  <h2 v-if="resultWinnerLooserObjArr.length > 0">Winner / Looser Configs</h2>

  <div class="row-container columns">

    <form v-for="(summary, summaryIndex) in resultWinnerLooserObjArr"
          :class="['form-horizontal', (summaryIndex % 2 === 0) && 'k-left', 'col-6 column']">

      <div class="col-12 col-sm-12">

        <!-- Fill with some tabular overview -->
        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="summary"
               :header="summary.metricName"
               :index="summaryIndex"
               summary-type="bestWorst"
        />

      </div>

    </form>

  </div>


  <h2 v-if="parameterEffectResultSummaryObjArr.length > 0">Parameter Effect</h2>

  <div class="row-container columns">

    <form v-for="(summary, summaryIndex) in parameterEffectResultSummaryObjArr"
          :class="['form-horizontal', (summaryIndex % 2 === 0) && 'k-left', 'col-6 column']">

      <div class="col-12 col-sm-12">

        <!-- Fill with some tabular overview -->
        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="summary"
               :header="summary.metricName"
               :index="summaryIndex"
               summary-type="effect"
        />

      </div>

    </form>

  </div>


</template>


<script>

import Table from "@/components/partials/Table.vue";

import {ResultSummary, RowsWithId} from "@/utils/resultSummaryObjects";
import {ref} from "vue";
import {numberAwareFormat} from "@/utils/dataFunctions";
import Button from "@/components/partials/controls/Button.vue";
import {jobResultSummaryRetrievalUrl} from "@/utils/globalConstants";
import {axiosCall} from "@/utils/retrievalFunctions";

export default {
  components: {Button, Table},
  methods: {},
  computed: {

    dateIdToJobIdsWithResultSummariesMapping() {
      return this.$store.state.resultState.availableDateIdToJobIdsWithResultsMapping
    }

  },

  setup(props, context) {

    let menuDropDownActive = ref(false)
    let selectedDateIDValue = ref("")
    let selectedJobIdValue = ref("")

    let parameterEffectResultSummaryObjArr = ref([])
    let resultWinnerLooserObjArr = ref([])

    document.addEventListener("click", closeDropdownIfClickOutsideOfControlElements)

    function loadSummaryData({dateId, jobId}) {
      console.info(`Load data for dateId '${dateId}', jobId '${jobId}'`)
      let url = jobResultSummaryRetrievalUrl
          .replace("#DATE_ID", dateId)
          .replace("#JOB_ID", jobId) + "?file=summary.json"
      axiosCall(
        url,
        "GET",
        undefined,
        resp => {
          let json = resp.data.data
          parameterEffectResultSummaryObjArr.value = Object.keys(json).map(metricName => {
            return resultJsonToEffectEstimateResultSummary(json[metricName])
          })
          resultWinnerLooserObjArr.value = Object.keys(json).map(metricName => {
            return resultJsonToWinnerLooserConfigResultSummary(json[metricName])
          })
        }
      )
    }

    function setSelectedDateId(value) {
      selectedDateIDValue.value = value
    }

    function setSelectedJobId(value) {
      selectedJobIdValue.value = value
    }

    /**
     * Toggles display of dropdown
     */
    function toggleMenuDropdown() {
      menuDropDownActive.value = !menuDropDownActive.value
    }

    /**
     * Close control menu in case
     * @param event
     */
    function closeDropdownIfClickOutsideOfControlElements(event) {
      let dropdown = document.getElementById(getDropdownElementId())
      let dropdownControls = document.getElementById(getDropdownControlsElementId())
      if (!dropdown.contains(event.target) && !dropdownControls.contains(event.target)) {
        if (menuDropDownActive.value) toggleMenuDropdown()
      }
    }

    function getDropdownControlsElementId() {
      return 'jobResultSummary-dataLoadCDropdownControl'
    }

    function getDropdownElementId() {
      return 'jobResultSummary-dataLoadDropdown'
    }

    /**
     * Format an array of values. If numerical, limit all to 4 decimal digits, otherwise leave as they are.
     * Then append all with delimiter as joining element. default delimiter: " &"
     * @param array
     * @param delimiter
     */
    function formatValueArray(array, delimiter = " & ") {
      return array.map(value => {
        return numberAwareFormat(value, 4)
      }).join(delimiter)
    }

    function resultJsonToWinnerLooserConfigResultSummary(json) {
      let usedMetricKey = "metric"
      let usedMetricName = json[usedMetricKey]
      let resultsKey = "results"
      let bestAndWorstConfigKey = "bestAndWorstConfigs"
      let bestConfigKey = "best"
      let worstConfigKey = "worst"
      let ids = Object.keys(json[resultsKey]).sort()
      let parameterNames = Object.keys(json[resultsKey][ids[0]][bestAndWorstConfigKey][bestConfigKey][0]).sort()
      let rowsWithIdArray = ids.map(id => {
        // configs are structured as {"param1": ["val1", "val2"], "param2": ["val1"],...}
        // thus taking keys gives the parameter names and the values for the keys
        // give the current (potentially multi-value) parameter setting
        let winnerConfig = json[resultsKey][id][bestAndWorstConfigKey][bestConfigKey][0]
        let winnerValue = json[resultsKey][id][bestAndWorstConfigKey][bestConfigKey][1]
        let looserConfig = json[resultsKey][id][bestAndWorstConfigKey][worstConfigKey][0]
        let looserValue = json[resultsKey][id][bestAndWorstConfigKey][worstConfigKey][1]

        console.debug(`winnerConfig: ${JSON.stringify(winnerConfig)}, looserConfig: ${JSON.stringify(looserConfig)}`)

        let parameterValuesWinnerConfig = parameterNames.map(name => formatValueArray(winnerConfig[name]))
        let parameterValuesLooserConfig = parameterNames.map(name => formatValueArray(looserConfig[name]))

        console.debug(`winnerParamConfig: ${JSON.stringify(parameterValuesWinnerConfig)}, looserParamConfig: ${JSON.stringify(parameterValuesLooserConfig)}`)

        // id is the tag here, values the parameter values and finally the metric value
        // we prepare two rows here per id, one for the best, other for the worst
        let winnerRow = [...parameterValuesWinnerConfig, numberAwareFormat(winnerValue, 4)]
        let looserRow = [...parameterValuesLooserConfig, numberAwareFormat(looserValue, 4)]
        return new RowsWithId(id, [winnerRow, looserRow])
      })
      return new ResultSummary(
          usedMetricName,
          [...parameterNames, "metric"],
          ["winner", "looser"],
          rowsWithIdArray
      )
    }

    /**
     * Filling the json result into data object where
     * functionality like sorting is baked into the object's methods
     * (see ResultSummary class)
     *
     * @param json
     * @returns {ResultSummary}
     */
    function resultJsonToEffectEstimateResultSummary(json) {
      let resultsKey = "results"
      let effectEstimateKey = "parameterEffectEstimate"
      let ids = Object.keys(json[resultsKey])
      let effectMeasureNames = Object.keys(json[resultsKey][ids[0]][effectEstimateKey])
      let parameterNames = Object.keys(json[resultsKey][ids[0]][effectEstimateKey][effectMeasureNames[0]])
      effectMeasureNames.sort().reverse()
      parameterNames.sort().reverse()
      let rowsWithIdArray = ids.map(id => {
        let valueRowsForId = effectMeasureNames.map(effectMeasure => {
          let paramNameToEffectValue = json[resultsKey][id][effectEstimateKey][effectMeasure]
          return parameterNames.map(name => paramNameToEffectValue[name])
              .map(numb => numb.toFixed(4))
        })
        return new RowsWithId(id, valueRowsForId)
      })
      let metricName = json["metric"]
      return new ResultSummary(
          metricName,
          parameterNames,
          effectMeasureNames,
          rowsWithIdArray
      )
    }

    function handleSortDataEvent({summaryType, resultIndex, measureIndex, columnIndex, decreasing}) {
      if (summaryType === "effect") {
        parameterEffectResultSummaryObjArr.value[resultIndex].sortByMeasureAndColumnByIndices(measureIndex, columnIndex, decreasing)
      }
      if (summaryType === "bestWorst") {
        resultWinnerLooserObjArr.value[resultIndex].sortByMeasureAndColumnByIndices(measureIndex, columnIndex, decreasing)
      }
    }

    function handleIdSort({summaryType, resultIndex, decreasing}) {
      if (summaryType === "effect") parameterEffectResultSummaryObjArr.value[resultIndex].idSort(decreasing)
      if (summaryType === "bestWorst") resultWinnerLooserObjArr.value[resultIndex].idSort(decreasing)
    }

    return {
      parameterEffectResultSummaryObjArr, handleSortDataEvent, handleIdSort,
      resultWinnerLooserObjArr, menuDropDownActive, toggleMenuDropdown, getDropdownControlsElementId,
      getDropdownElementId, selectedDateIDValue, setSelectedDateId,
      selectedJobIdValue, setSelectedJobId, loadSummaryData
    }
  }

}

</script>


<style scoped>

.row-container {
  margin: 3em;
}

.form-horizontal.k-left {
  border-right: .05rem solid #353535;
}

.form-horizontal {
  border-top: .05rem solid #353535;
}

.k-tableControls {
  margin-right: 1em;
  margin-bottom: 5em;
}

ul.menu {
  text-align: left;
}

.dropdownContainer {
  position: relative;
}

.dropdown {
  position: absolute;
  z-index: 10;
}

.btn.k-dropdown-button {
  width: 8em;
  padding-bottom: 2em;
}

.dropdownControl {
  margin-bottom: 2em;
  font-size: x-large;
  font-weight: bolder;
}


</style>
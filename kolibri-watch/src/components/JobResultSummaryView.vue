<template>

  <h2>Winner / Looser Configs</h2>

  <div class="row-container columns">

    <form v-for="(summary, summaryIndex) in resultWinnerLooserObjArr" :class="['form-horizontal', (summaryIndex % 2 === 0) && 'k-left', 'col-6 column']">

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





  <h2>Parameter Effect</h2>

  <div class="row-container columns">

    <form v-for="(summary, summaryIndex) in parameterEffectResultSummaryObjArr" :class="['form-horizontal', (summaryIndex % 2 === 0) && 'k-left', 'col-6 column']">

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

import json from "../data/JobSummaryExamples.json";
import {ResultSummary, RowsWithId} from "@/utils/resultSummaryObjects";
import {ref} from "vue";
import {numberAwareFormat} from "@/utils/dataFunctions";

export default {
  components: {Table},
  methods: {},
  computed: {},

  setup(props) {

    let parameterEffectResultSummaryObjArr = ref(
        Object.keys(json).map(metricName => {
          return resultJsonToEffectEstimateResultSummary(json[metricName])
        })
    )

    let resultWinnerLooserObjArr = ref(
        Object.keys(json).map(metricName => {
          return resultJsonToWinnerLooserConfigResultSummary(json[metricName])
        })
    )

    console.debug(resultWinnerLooserObjArr.value)

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

    return {parameterEffectResultSummaryObjArr, handleSortDataEvent, handleIdSort,
      resultWinnerLooserObjArr}
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


</style>
<template>

  <div class="row-container columns">

    <form v-for="(summary, summaryIndex) in resultSummaryObjArr" :class="['form-horizontal', (summaryIndex % 2 === 0) && 'k-left', 'col-6 column']">

      <div class="col-12 col-sm-12">

        <!-- Fill with some tabular overview -->
        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="summary"
               :header="summary.metricName"
               :index="summaryIndex"
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

export default {
  components: {Table},
  methods: {},
  computed: {},

  setup(props) {

    let resultSummaryObjArr = ref(
        Object.keys(json).map(metricName => {
          return resultJsonToObj(json[metricName])
        })
    )

    /**
     * Filling the json result into data object where
     * functionality like sorting is baked into the object's methods
     * (see ResultSummary class)
     *
     * @param json
     * @returns {ResultSummary}
     */
    function resultJsonToObj(json) {
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

    function handleSortDataEvent({resultIndex, measureIndex, columnIndex, decreasing}) {
      resultSummaryObjArr.value[resultIndex].sortByMeasureAndColumnByIndices(measureIndex, columnIndex, decreasing)
    }

    function handleIdSort({resultIndex, decreasing}) {
      resultSummaryObjArr.value[resultIndex].idSort(decreasing)
    }

    return {resultSummaryObjArr, handleSortDataEvent, handleIdSort}
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
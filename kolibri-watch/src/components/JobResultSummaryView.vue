<template>

  <div class="row-container columns">

    <form class="form-horizontal k-left col-6 column">

      <div class="col-12 col-sm-12">

        <!-- Fill with some tabular overview -->
        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="resultSummaryObjArr[0]"
               :header="resultSummaryObjArr[0].metricName"
               index="0"
        />

      </div>

    </form>

    <form class="form-horizontal col-6 column">

      <div class="col-12 col-sm-12">

        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="resultSummaryObjArr[1]"
               :header="resultSummaryObjArr[1].metricName"
               index="1"
        />

      </div>

    </form>

    <form class="form-horizontal k-left col-6 column">

      <div class="col-12 col-sm-12">

        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="resultSummaryObjArr[2]"
               :header="resultSummaryObjArr[2].metricName"
               index="2"
        />

      </div>

    </form>

    <form class="form-horizontal col-6 column">

      <div class="col-12 col-sm-12">

        <Table @sort-data="handleSortDataEvent"
               @id-sort="handleIdSort"
               :result-summary="resultSummaryObjArr[3]"
               :header="resultSummaryObjArr[3].metricName"
               index="3"
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

    let resultSummaryObjArr = ref([
        resultJsonToObj(json),
        resultJsonToObj(json),
        resultJsonToObj(json),
        resultJsonToObj(json)
    ])

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
  border-right: .05rem solid black;
}

.form-horizontal {
  border-top: .05rem solid black;
}


</style>
<template>

  <div class="row-container columns">

    <form class="form-horizontal col-6 column">

      <div class="col-12 col-sm-12">

        <!-- Fill with some tabular overview -->
        <Table
            :column-headers="columnHeaders"
            :data="dataRows"
            :header="'Summary: ' + metricName"
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
  methods: {

    /**
     * Extract value rows for given tag with values sorted according to the sequence given by the sorted parameter names
     * @param tag
     * @returns {{maxSingleResultShift: *, maxMedianShift: *}}
     */
    rowForTag(tag) {
      let sortedParameterNames = this.sortedParameterNames
      let maxMedianShift = json["results"][tag]["parameterEffectEstimate"]["maxMedianShift"]
      let maxSingleResultShift = json["results"][tag]["parameterEffectEstimate"]["maxSingleResultShift"]

      // rows, order by sorted parameters
      let maxMedianShiftSequence = sortedParameterNames.map(name => maxMedianShift[name])
          .map(numb => numb.toFixed(4))
      let maxSingleResultShiftSequence = sortedParameterNames.map(name => maxSingleResultShift[name])
          .map(numb => numb.toFixed(4))

      return {
        "maxMedianShift": maxMedianShiftSequence,
        "maxSingleResultShift": maxSingleResultShiftSequence
      }
    },
  },
  computed: {

    metricName(){
      return json["metric"]
    },

    tags() {
      return Object.keys(json["results"])
    },

    sortedParameterNames(){
      let parameterNames = Object.keys(json["results"][this.tags[0]]["parameterEffectEstimate"]["maxMedianShift"])
      parameterNames.sort().reverse()
      console.info("Sorted Parameter Names:")
      console.log(parameterNames)
      return parameterNames
    },

    columnHeaders() {
      return ["tag", ...this.sortedParameterNames]
    },

    /**
     * Now bake tag values together with their respective row such that we have
     * an array of arrays, each being one row to display.
     *
     * Right now we have the measures "maxMedianShift" and "maxSingleResultShift".
     * The former calculates the median of the value distribution for all distinct values
     * of the respective parameter, and computes maxValue - minValue.
     * For maxSingleResultShift, all results with same other parameters but single parameter
     * varied are compared, and maxValue - minValue is computed.
     * [
     * [tag1, measure1-1, measure1-2, measure1-3],
     * [tag2, measure1-1, measure1-2, measure1-3]
     * ]
     * @returns {*}
     */
    dataRows(){
      let measure = 'maxMedianShift'
      let tags = this.tags
      tags.sort().reverse()
      // these still contain two lines per tag value
      let dataRowsForTags = tags.map(tag => this.rowForTag(tag))
      return dataRowsForTags.map(function(el, ind){
        return [tags[ind], ...el[measure]]
      })
    },

  },

  setup(props) {

    // console.log(json)
    let resultSummaryObj = ref(resultJsonToObj(json))

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
      return new ResultSummary(
          parameterNames,
          effectMeasureNames,
          rowsWithIdArray
      )
    }

    return {}
  }

}

</script>


<style scoped>

.row-container {
  margin: 3em;
}


</style>
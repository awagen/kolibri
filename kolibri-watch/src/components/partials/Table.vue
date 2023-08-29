<template>

  <!-- TODO: need controls for selection of the current sort criterion for the values
   and possibility to hide the additional data points  and to display a legend in case
   multiple data values are selected -->

  <h4 class="tableHeader">{{ header }}</h4>
  <div class="k-table-wrapper">
    <table class="table">
      <thead>
      <tr>
        <th class="firstColumn">
          <span class="columnTitle">ID</span>
          <i v-if="resultSummary.sortedById && resultSummary.idSortDecreasing" class="icon icon-arrow-down"
             @click.prevent="handleIDSortEvent({decreasing: false})"></i>
          <i v-if="resultSummary.sortedById && !resultSummary.idSortDecreasing" class="icon icon-arrow-up"
             @click.prevent="handleIDSortEvent({decreasing: true})"></i>
          <i v-if="!resultSummary.sortedById" class="icon icon-minus"
             @click.prevent="handleIDSortEvent({decreasing: true})"></i>
        </th>
        <th v-for="(headerColumn, headerColumnIndex) in resultSummary.columnNames">
          <span class="columnTitle">{{ headerColumn }}</span>
          <i v-if="resultSummary.sortedByColumn[headerColumnIndex] && resultSummary.columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-down"
             @click.prevent="handleSortEvent({measureIndex: 1, columnIndex: headerColumnIndex, decreasing: false})"></i>
          <i v-if="resultSummary.sortedByColumn[headerColumnIndex] && !resultSummary.columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-up"
             @click.prevent="handleSortEvent({measureIndex: 1, columnIndex: headerColumnIndex, decreasing: true})"></i>
          <i v-if="!resultSummary.sortedByColumn[headerColumnIndex]" class="icon icon-minus"
             @click.prevent="handleSortEvent({measureIndex: 1, columnIndex: headerColumnIndex, decreasing: true})"></i>
        </th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="(rowsWithId, rowIndex) in resultSummary.values">

        <td :class="[ rowIndex % 2 === 0 ? 'rowEven' : 'rowUneven', 'firstColumn' ]">
          {{ rowsWithId.id }}
        </td>

        <td :class="[ rowIndex % 2 === 0 ? 'rowEven' : 'rowUneven']"
            v-for="(_, columnIndex) in rowsWithId.rows[0]">

          <template v-for="(subRow, subRowIndex) in rowsWithId.rows">
            <div class="bar bar-sm">
              <div class="bar-item" role="progressbar"
                   :style="{'width': measureToScaledValue(subRow[columnIndex], resultSummary.maxMeasureValue) * 100 + '%', 'background': heatMapColorForValue(measureToScaledValue(subRow[columnIndex], resultSummary.maxMeasureValue))}" :aria-valuenow="(measureToScaledValue(subRow[columnIndex], resultSummary.maxMeasureValue)) * 100"
                   aria-valuemin="0" aria-valuemax="100"></div>
            </div>
            <div>{{ subRow[columnIndex] }}</div>
          </template>

        </td>

      </tr>
      </tbody>
    </table>
  </div>
</template>

<script>


import {ResultSummary} from "@/utils/resultSummaryObjects";

export default {
  props: {
    resultSummary: {
      type: ResultSummary,
      required: false
    },
    header: {
      type: String,
      required: false
    }
  },
  setup(props, context) {


    /**
     * Given a value and a max value, scale to fraction of scaled value and limit to 1.0
     *
     * @param measure
     * @param maxValue
     * @returns {number}
     */
    function measureToScaledFraction(measure, maxValue) {
      return Math.max(0.0, Math.min(1.0, measure / maxValue))
    }

    /**
     * Pass value within [0, 1]. Returns hsl color value that fades from red to green as the value increases.
     * @param value
     * @returns {string}
     */
    function heatMapColorForValue(value) {
      // effectively restricting to colors between green and red and turning it around such that smaller values are
      // towards red, bigger towards green
      let adjustedValue = -(value * 0.5 + 0.5)
      let h = (1.0 - adjustedValue) * 240
      return "hsl(" + h + ", 100%, 50%)";
    }

    function handleSortEvent({measureIndex, columnIndex, decreasing}) {
      context.emit("sortData", {measureIndex, columnIndex, decreasing})
    }

    function handleIDSortEvent({decreasing}) {
      context.emit("idSort", {decreasing})
    }

    return {
      handleSortEvent, handleIDSortEvent, heatMapColorForValue, measureToScaledValue: measureToScaledFraction
    }

  },
  emits: ["sortData", "idSort"]

}

</script>


<style scoped>

.k-table-wrapper {
  display: inline-block;
  height: auto;
  max-height: 60em;
  overflow: auto;
  width: 100%;
}

/* keeps the header at the top while scrolling */
table thead {
  position: -webkit-sticky;
  position: sticky;
  top: 0;
  background-color: #25333C;
}

table {
  border-collapse: collapse;
  width: 100%;
  /* set column width based on width of cells in first row */
  table-layout: fixed;

  font-size: medium;
  color: #9C9C9C;
  border-color: darkgrey;
}

tbody > tr, thead {
  background-color: #233038;
}

th, td {
  border-color: black !important;
}

th .columnTitle {
  margin-right: 0.5em;
}

td, th {
  border-bottom: none !important;
}

td.rowEven {
  background-color: #283540;
}

.firstColumn {
  border-right: 1px solid #383850 !important;
}

.tableHeader {
  padding-top: 1em;
}

</style>
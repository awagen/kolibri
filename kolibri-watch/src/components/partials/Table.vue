<template>

  <h4 class="tableHeader">{{ header }}</h4>
  <div class="k-table-wrapper">
    <table class="table">
      <thead>
      <tr>
<!--        <th>nr</th>-->
        <th :class="headerColumnIndex === 0 && 'firstColumn'" v-for="(headerColumn, headerColumnIndex) in columnHeaders">
          <span class="columnTitle">{{ headerColumn }}</span>
          <i v-if="columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-down" @click.prevent="invertSort(headerColumnIndex)"></i>
          <i v-if="!columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-up" @click.prevent="invertSort(headerColumnIndex)"></i>
        </th>
      </tr>
      </thead>
      <tbody>
      <!-- TODO: the current format has a value array as row. Since we have different criteria of evaluation,
       we actually need an array of arrays of values and the sequence of related evaluation criteria and then
       offer a sorting according to the selected measure -->
      <tr v-for="(row, rowIndex) in currentData">
<!--        <td>{{index}}</td>-->
        <!-- pickHex([0, 255, 0], [255,0,0], 0.80 ) -->
        <td :class="[ rowIndex % 2 === 0 ? 'rowEven' : 'rowUneven', columnIndex === 0 && 'firstColumn' ]"
            v-for="(column, columnIndex) in row">
          <div v-if="columnIndex > 0" class="bar bar-sm">
            <!-- TODO: for now make the color weight the actual value divided by the biggest difference there is / biggest value
             and limit the colors between [0.5 , 1.0]-->
            <div class="bar-item" role="progressbar" :style="{'width': 40 + '%', 'background': heatMapColorForValue(1.0)}" aria-valuenow="95" aria-valuemin="0" aria-valuemax="100"></div>
          </div>
          {{ column }}
          <template v-if="columnIndex > 0">
            <div class="bar bar-sm">
              <div class="bar-item" role="progressbar" :style="{'width': 40 + '%', 'background': heatMapColorForValue(0.0)}" aria-valuenow="95" aria-valuemin="0" aria-valuemax="100"></div>
            </div>
            {{ column }}
          </template>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script>


import {ref} from "vue";
import {numberAwareComparison} from "@/utils/dataFunctions";

export default {
  // methods: {math},
  /**
   * columnHeaders: Assumes array of header names
   * data: Assumes array of arrays, where each single array holds the column values
   * corresponding to the header for the respective array-index
   */
  props: {
    columnHeaders: {
      type: Array,
      required: true,
      default: []
    },
    data: {
      type: Array[Array],
      required: true,
      default: []
    },
    header: {
      type: String,
      required: false,
      default: ""
    }
  },
  setup(props){


    let columnSortDecreasing = ref(props.columnHeaders.map(_ => true))

    let currentData = ref(props.data.map(_ => _))

    function sortData(index, decreasing) {
      // sort the actual data according to sort direction
      let sortedArr = [...currentData.value].sort(function(seq1, seq2) {
        return numberAwareComparison(seq1[index], seq2[index])
      })
      if (decreasing) sortedArr.reverse()
      return sortedArr
    }

    /**
     * Based on current sort order, change direction of sorting.
     * @param index
     */
    function invertSort(index) {
      // change the arrow direction
      let decreasing = !columnSortDecreasing.value[index]
      columnSortDecreasing.value[index] = decreasing
      currentData.value = sortData(index, decreasing)
    }

    /* value within [0, 1] */
    function heatMapColorForValue(value){
      // effectively restricting to colors between green and redand turning it around such that smaller values are
      // towards red, bigger towards green
      let adjustedValue = -(value * 0.5 + 0.5)
      let h = (1.0 - adjustedValue) * 240
      return "hsl(" + h + ", 100%, 50%)";
    }

    return {
      columnSortDecreasing, heatMapColorForValue, invertSort, currentData
    }

  }

}

</script>



<style scoped>

.k-table-wrapper {
  display: inline-block;
  height: auto;
  max-height:60em;
  overflow: auto;
  width:100%;
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
  table-layout: fixed ;

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
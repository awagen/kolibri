<template>

  <h4 class="tableHeader">{{ header }}</h4>

  <!-- Selection menu to refine whats displayed, what the sorting criterion is and the like
       NOTE: put in separate component
  -->
  <!-- Dropdown activate / deactivate -->
  <div :id="getDropdownControlsElementId()">
    <span class="k-tableControls">Controls</span>
    <i :class="['icon', menuDropDownActive ? 'icon-arrow-down' : 'icon-arrow-up']" @click.prevent="toggleMenuDropdown"></i>
  </div>
  <!-- dropdown content -->
  <div class="dropdownContainer">
    <div :id="getDropdownElementId()" :class="['dropdown', menuDropDownActive && 'active']">

      <ul class="menu">
        <li class="divider" data-content="Sorting Measure"/>
        <!-- Sort measure selection -->
        <template v-for="(element, index) in resultSummary.measureNames">
          <li class="menu-item">
            <label class="form-radio form-inline">
              <input type="radio"
                     :name="resultSummary.metricName + '-sortCriterion'"
                     :value="element"
                     :checked="(sortByMeasureIndex === index) ? '' : null"
                     @change="setSortByMeasureIndex(index)"
              />
              <i class="form-icon"></i>
              {{ element }}
            </label>
          </li>
        </template>

        <li class="divider" data-content="Displayed Measure"/>
        <template v-for="(element, index) in resultSummary.measureNames">
          <li class="menu-item">
            <label class="form-checkbox form-inline">
              <input :id="[getDisplayedMeasureSelectionId() + '-' + index]"
                     :class="[getDisplayedMeasureSelectionId()]"
                     :checked="displayedMeasures.includes(element)"
                     type="checkbox"
                     :name="resultSummary.metricName + '-displayCriterion-' + index"
                     :value="element"
                     @change="handleMeasureDisplaySelectionChange()"
              />
              <i class="form-icon"></i>
              {{ element }}
            </label>
          </li>
        </template>

      </ul>

    </div>
  </div>

  <div class="k-table-wrapper">
    <table class="table">
      <thead>
      <tr>
        <th class="firstColumn">
          <span class="columnTitle">ID</span>
          <i v-if="resultSummary.sortedById && resultSummary.idSortDecreasing" class="icon icon-arrow-down"
             @click.prevent="handleIDSortEvent({summaryType: summaryType, resultIndex: index, decreasing: false})"></i>
          <i v-if="resultSummary.sortedById && !resultSummary.idSortDecreasing" class="icon icon-arrow-up"
             @click.prevent="handleIDSortEvent({summaryType: summaryType, resultIndex: index, decreasing: true})"></i>
          <i v-if="!resultSummary.sortedById" class="icon icon-minus"
             @click.prevent="handleIDSortEvent({summaryType: summaryType, resultIndex: index, decreasing: true})"></i>
        </th>
        <th v-for="(headerColumn, headerColumnIndex) in resultSummary.columnNames">
          <span class="columnTitle">{{ headerColumn }}</span>
          <i v-if="resultSummary.sortedByColumn[headerColumnIndex] && resultSummary.columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-down"
             @click.prevent="handleSortEvent({summaryType: summaryType, resultIndex: index, measureIndex: sortByMeasureIndex, columnIndex: headerColumnIndex, decreasing: false})"></i>
          <i v-if="resultSummary.sortedByColumn[headerColumnIndex] && !resultSummary.columnSortDecreasing[headerColumnIndex]" class="icon icon-arrow-up"
             @click.prevent="handleSortEvent({summaryType: summaryType, resultIndex: index, measureIndex: sortByMeasureIndex, columnIndex: headerColumnIndex, decreasing: true})"></i>
          <i v-if="!resultSummary.sortedByColumn[headerColumnIndex]" class="icon icon-minus"
             @click.prevent="handleSortEvent({summaryType: summaryType, resultIndex: index, measureIndex: sortByMeasureIndex, columnIndex: headerColumnIndex, decreasing: true})"></i>
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
            <!-- Only selecting those rows that match the selected measures -->
            <template v-if="displayedMeasuresIndices.includes(subRowIndex)">
              <div v-if="summaryType === 'effect' || isNumeric(subRow[columnIndex])" class="bar bar-sm">
                <div class="bar-item" role="progressbar"
                     :style="{'width': measureToScaledFraction(subRow[columnIndex], resultSummary.maxMeasureValue) * 100 + '%', 'background': heatMapColorForValue(measureToScaledFraction(subRow[columnIndex], resultSummary.maxMeasureValue))}" :aria-valuenow="(measureToScaledFraction(subRow[columnIndex], resultSummary.maxMeasureValue)) * 100"
                     aria-valuemin="0" aria-valuemax="100"></div>
              </div>
              <div>{{ subRow[columnIndex] }}</div>
            </template>
          </template>

        </td>

      </tr>
      </tbody>
    </table>
  </div>
</template>

<script>


import {ResultSummary} from "@/utils/resultSummaryObjects";
import {ref} from "vue";
import {range} from "lodash";
import {isNumeric} from "@/utils/dataFunctions";

export default {
  methods: {isNumeric},
  props: {
    resultSummary: {
      type: ResultSummary,
      required: true
    },
    header: {
      type: String,
      required: false
    },
    index: {
      type: Number,
      required: true
    },
    summaryType: {
      type: String,
      required: true,
      validator(value) {
        return ["effect", "bestWorst"].includes(value)
      }

    }
  },
  setup(props, context) {

    let sortByMeasureIndex = ref(0)
    let menuDropDownActive = ref(false)
    let displayedMeasures = ref(props.resultSummary.measureNames.map(_ => _))
    let displayedMeasuresIndices = ref(range(0, props.resultSummary.measureNames.length, 1))

    document.addEventListener("click", closeDropdownIfClickOutsideOfControlElements)

    function getDisplayedMeasureSelectionClass(){
      return 'displayMeasureSelection-' + props.index
    }

    function getDropdownControlsElementId(){
      return props.resultSummary.metricName + '-summaryControls-' + props.summaryType + "-" + props.index
    }

    function getDropdownElementId(){
      return props.resultSummary.metricName + '-summaryMenu-' + props.summaryType + "-" + props.index
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

    /**
     * Toggles display of dropdown
     */
    function toggleMenuDropdown(){
      menuDropDownActive.value = !menuDropDownActive.value
    }

    function selectedMeasureIndices() {
      return displayedMeasures.value.map(measure => props.resultSummary.measureNames.indexOf(measure))
    }

    function handleMeasureDisplaySelectionChange(){
      let measureSelection = Array.from(document.getElementsByClassName(getDisplayedMeasureSelectionClass()))
           .filter(el => el.checked)
           .map(el => el.getAttribute("value"))
      displayedMeasures.value = measureSelection
      displayedMeasuresIndices.value = selectedMeasureIndices()
      console.debug(`measure selection: ${measureSelection}`)
    }

    function setSortByMeasureIndex(index) {
      if (index < props.resultSummary.measureNames.length) {
        sortByMeasureIndex.value = index
      }
    }


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

    function handleSortEvent({summaryType, resultIndex, measureIndex, columnIndex, decreasing}) {
      context.emit("sortData", {summaryType, resultIndex, measureIndex, columnIndex, decreasing})
    }

    function handleIDSortEvent({summaryType, resultIndex, decreasing}) {
      context.emit("idSort", {summaryType, resultIndex, decreasing})
    }

    return {
      handleSortEvent,
      handleIDSortEvent,
      heatMapColorForValue,
      measureToScaledFraction,
      sortByMeasureIndex,
      setSortByMeasureIndex,
      menuDropDownActive,
      toggleMenuDropdown,
      handleMeasureDisplaySelectionChange,
      displayedMeasures,
      displayedMeasuresIndices,
      getDropdownControlsElementId,
      getDropdownElementId,
      getDisplayedMeasureSelectionId: getDisplayedMeasureSelectionClass
    }

  },
  emits: ["sortData", "idSort"]

}

</script>


<style scoped>

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

.k-tableControls {
  margin-right: 1em;
}

.k-table-wrapper {
  display: inline-block;
  height: auto;
  max-height: 80em;
  width: 100%;
  overflow-x: auto;
  overflow-y: auto;
}

/* keeps the header at the top while scrolling */
table thead {
  /* position: -webkit-sticky; */
  position: sticky;
  top: 0;
  background-color: #25333C;
  /* need to set z-index, otherwise data scrolls transparently through it */
  z-index: 5;
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
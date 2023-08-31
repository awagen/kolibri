<template>

  <div class="viewContainer" @click="closeDropdownIfClickOutsideOfControlElements">

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
          <template
              v-for="(dateId, dateIndex) in [...[...Object.keys(dateIdToJobIdsWithResultSummariesMapping)].sort()].reverse()">
            <li class="menu-item">
              <label class="form-radio form-inline">
                <input type="radio"
                       :name="'dateId-select-' + dateIndex"
                       :value="dateId"
                       :checked="(selectedDateId === dateId) ? '' : null"
                       @change="setSelectedDateId(dateId)"
                />
                <i class="form-icon"></i>
                {{ dateId }}
              </label>
            </li>
          </template>

          <li class="divider" data-content="JobId Selection"/>
          <!-- JobId selection -->
          <template v-for="(jobId, jobIndex) in dateIdToJobIdsWithResultSummariesMapping[selectedDateId]">
            <li class="menu-item">
              <label class="form-radio form-inline">
                <input type="radio"
                       :name="'jobId-select-' + jobIndex"
                       :value="jobId"
                       :checked="(selectedJobId === jobId) ? '' : null"
                       @change="setSelectedJobId(jobId)"
                />
                <i class="form-icon"></i>
                {{ jobId }}
              </label>
            </li>
          </template>

          <li class="divider" data-content="Action"/>
          <!-- summary load button -->
          <template v-if="selectedJobId !== '' && selectedDateId !== ''">
            <li class="menu-item">

              <!-- change to button -->
              <Button
                  class="k-dropdown-button"
                  emitted-event-name="loadSummaryData"
                  :emitted-event-arguments="{dateId: selectedDateId, jobId: selectedJobId}"
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


    <h2 v-if="selectedResultWinnerLooserObjArr.length > 0">Winner / Looser Configs</h2>

    <div class="row-container columns">

      <form v-for="(summary, summaryIndex) in selectedResultWinnerLooserObjArr"
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


    <h2 v-if="selectedParameterEffectResultSummaryObjArr.length > 0">Parameter Effect</h2>

    <div class="row-container columns">

      <form v-for="(summary, summaryIndex) in selectedParameterEffectResultSummaryObjArr"
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

  </div>

</template>


<script>

import Table from "@/components/partials/Table.vue";

import {ref} from "vue";
import Button from "@/components/partials/controls/Button.vue";
import {useStore} from "vuex";

export default {
  components: {Button, Table},
  methods: {},
  computed: {

    selectedParameterEffectResultSummaryObjArr() {
      return this.$store.state.resultSummaryState.selectedParameterEffectResultSummaryObjArr
    },

    selectedResultWinnerLooserObjArr() {
      return this.$store.state.resultSummaryState.selectedResultWinnerLooserObjArr
    },

    dateIdToJobIdsWithResultSummariesMapping() {
      return this.$store.state.resultSummaryState.availableDateIdToJobIdsWithResultsMapping
    },

    selectedJobId() {
      return this.$store.state.resultSummaryState.selectedJobIDValue
    },

    selectedDateId() {
      return this.$store.state.resultSummaryState.selectedDateIDValue
    }

  },

  setup(props, context) {

    let store = useStore()

    let menuDropDownActive = ref(false)

    function loadSummaryData({dateId, jobId}) {
      store.commit("updateSelectedResultSummaryData", {dateId, jobId})
    }

    function setSelectedDateId(value) {
      store.commit("setSelectedResultSummaryDateID", value)
    }

    function setSelectedJobId(value) {
      store.commit("setSelectedResultSummaryJobID", value)
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

    function handleSortDataEvent({summaryType, resultIndex, measureIndex, columnIndex, decreasing}) {
      store.commit("sortSelectedResultSummaryDataByData", {
        summaryType,
        resultIndex,
        measureIndex,
        columnIndex,
        decreasing
      })
    }

    function handleIdSort({summaryType, resultIndex, decreasing}) {
      store.commit("sortSelectedResultSummaryDataById", {summaryType, resultIndex, decreasing})
    }

    return {
      handleSortDataEvent, handleIdSort,
      menuDropDownActive, toggleMenuDropdown, getDropdownControlsElementId,
      getDropdownElementId, setSelectedDateId,
      setSelectedJobId, loadSummaryData, closeDropdownIfClickOutsideOfControlElements
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
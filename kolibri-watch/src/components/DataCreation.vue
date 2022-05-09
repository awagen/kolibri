<template>

  <h2 class="nodeListHeader">DATA</h2>

  <DataFileSelectTabs :file-data-by-type-mapping="this.$store.state.dataState.standaloneFileDataByType"
                      :selected-data-file-type="this.$store.state.dataState.selectedStandaloneDataFileType"
                      @file-type-update-call="selectStandaloneDataFileType"/>

  <template v-for="(dataValues, dataType, _) in this.$store.state.dataState.standaloneFileDataByType">
    <template v-if="dataType === this.$store.state.dataState.selectedStandaloneDataFileType">
      <DataFileOverview :show-add-button="true"
                        :show-delete-button="false"
                        :show-add-as-key-button="true"
                        :dataFileObjArray="dataValues"
                        :is-mappings="false"
      />
    </template>
  </template>

  <div class="divider"></div>

  <h2 class="nodeListHeader">MAPPED DATA</h2>

  <DataFileSelectTabs :file-data-by-type-mapping="this.$store.state.dataState.mappingFileDataByType"
                      :selected-data-file-type="this.$store.state.dataState.selectedMappingDataFileType"
                      @file-type-update-call="selectMappingDataFileType"
  />

  <template v-for="(dataValues, dataType, _) in this.$store.state.dataState.mappingFileDataByType">
    <template v-if="dataType === this.$store.state.dataState.selectedMappingDataFileType">
      <DataFileOverview :show-add-button="true"
                        :show-delete-button="false"
                        :show-add-as-key-button="false"
                        :dataFileObjArray="dataValues"
                        :is-mappings="true"/>
    </template>
  </template>

  <div class="divider"></div>

  <h2 class="nodeListHeader">COMPOSER</h2>
  <button @click="this.$store.commit('retrieveRequestSamplesForSelectedData')" class="btn btn-primary k-action-green">
    Retrieve Sample
    Requests
  </button>

  <div class="columns">

    <form class="form-horizontal col-6 column">
      <DataComposerOverview :dataFileObjArray="this.$store.state.dataState.selectedData"/>
      <div class="form-separator"></div>
    </form>

    <form class="form-horizontal col-6 column">
      <div class="row-container">
        <div class="accordion top">
          <input type="checkbox" id="accordion-1" name="accordion-checkbox" hidden>
          <label class="accordion-header" for="accordion-1">
            <i class="icon icon-arrow-right mr-1"></i>
            Composition Json
          </label>
          <div class="accordion-body">
            <pre id="template-content-display-1" v-html="this.$store.state.dataState.selectedDataJsonString"/>
          </div>
        </div>
        <div class="form-separator"></div>
        <div class="accordion">
          <input type="checkbox" id="accordion-2" name="accordion-checkbox" hidden>
          <label class="accordion-header" for="accordion-2">
            <i class="icon icon-arrow-right mr-1"></i>
            Sample Requests
          </label>
          <div class="accordion-body">
            <template v-for="(request, index) in this.$store.state.dataState.selectedDataRequestSamples">
              <div class="divider"></div>
              <div class="accordion nested">
                <input type="checkbox" :id="'accordion-inner-' + index" name="accordion-checkbox" hidden>
                <label class="accordion-header" :for="'accordion-inner-' + index">
                  <i class="icon icon-arrow-right mr-1"></i>
                  {{ request.request }}
                </label>
                <div class="accordion-body">
                  <div>
                    BODY: {{ request.body }}
                  </div>
                  <div>
                    HEADER: {{ request.header }}
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </form>
  </div>

  <div class="divider"></div>

  <!-- some metric information / selection -->
  <div class="row-container">
  <div class="accordion col-12">

    <input type="checkbox" id="accordion-3" name="accordion-checkbox" hidden>

    <label class="accordion-header" for="accordion-3">
      <i class="icon icon-arrow-right mr-1"></i>
      <h2 class="k-title">IR Metrics</h2>
    </label>

    <div class="accordion-body metric">

      <div class="columns">
        <form class="form-horizontal col-6 column">
          <template v-for="(metric, _) in this.$store.state.metricState.availableIRMetrics">
            <div class="divider"></div>
            <div class="form-group metric">
              <span>{{metric.type}}</span>
            </div>
            <template v-for="(value, propertyName, index) in metric">
              <template v-if="!propertyName.endsWith('_type') && propertyName !== 'type'">
              <div class="form-group metric">
                <label class="form-inline">
                  {{propertyName}}
                  <input v-if="['INT'].indexOf(metric[propertyName + '_type']) >= 0" class="form-input metric" type="number" step=1 v-model="metric[propertyName]">
                  <input v-if="['FLOAT'].indexOf(metric[propertyName + '_type']) >= 0" class="form-input metric" type="number" step=0.0001 v-model="metric[propertyName]">
                  <input v-if="metric[propertyName + '_type'] === 'STRING'" class="form-input metric" type="text" v-model="metric[propertyName]">
                </label>
              </div>
              </template>
            </template>
            <button @click.prevent="this.$store.commit('addIRMetricToSelected', metric.type)" class="btn btn-action k-add-button s-circle">
              <i class="icon icon-plus"></i>
            </button>
          </template>

          <div class="divider"></div>
          <template v-for="selectedMetric in this.$store.state.metricState.selectedIRMetrics">
            <span class="chip k-chip">
              {{ selectedMetric.kId }}
              <a @click.prevent="this.$store.commit('removeMetricIdFromSelected', selectedMetric.kId)" href="#" class="btn btn-clear" aria-label="Close" role="button"></a>
            </span>
          </template>

        </form>

        <!-- current metric composition overview -->
        <form class="form-horizontal col-6 column">
            <pre id="metrics-content-display" v-html="this.$store.state.metricState.selectedIRMetricsFormattedJsonString"/>
        </form>
      </div>
    </div>

  </div>
  </div>




</template>

<script>

import {onMounted} from "vue";
import DataFileSelectTabs from "../components/partials/DataFileSelectTabs.vue";
import DataFileOverview from "../components/partials/DataFileOverview.vue";
import DataComposerOverview from "../components/partials/DataComposerOverview.vue";

export default {

  props: [],
  components: {DataFileSelectTabs, DataFileOverview, DataComposerOverview},
  methods: {

    selectStandaloneDataFileType(fileType) {
      this.$store.commit("updateSelectedStandaloneDataFileType", fileType)
    },
    selectMappingDataFileType(fileType) {
      this.$store.commit("updateSelectedMappingDataFileType", fileType)
    }
  },
  setup(props) {
    onMounted(() => {
    })
    return {}
  }

}

</script>

<style scoped>

.accordion-body.metric {
  text-align: left;
}

.form-group.metric {
  display: inline-block;
  margin-left: 2em;
  width: 10em;
}

.form-input.metric {
  display: inline-block;
}

.row-container {
  margin: 3em;
}

.accordion.nested .accordion-header {
  margin-left: 1em;
}

.accordion .accordion-header {
  text-align: left;
}

button.k-add-button {
  background-color: transparent;
  border-width: 0;
  color: white;
}

button.k-add-button:hover {
  background-color: #588274;
}

button.k-action-green {
  background-color: #588274;
  border: none;
}

.accordion input:checked ~ .accordion-body, .accordion[open] .accordion-body {
  max-height: 100%;
}

.divider {
  border-color: #353535;
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

pre#template-content-display-1 {

  margin-top: 2em;

}

h2.k-title {
  display: inline-block;
  padding-left: 1em;
}

.chip.k-chip {
  color:black;
  margin-top:.5em;
}

</style>




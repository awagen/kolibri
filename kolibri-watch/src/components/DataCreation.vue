<template>

  <h2 class="nodeListHeader">DATA</h2>

  <DataFileSelectTabs :file-data-by-type-mapping="this.$store.state.standaloneFileDataByType"
                      :selected-data-file-type="this.$store.state.selectedStandaloneDataFileType"
                      @file-type-update-call="selectStandaloneDataFileType"/>

  <template v-for="(dataValues, dataType, _) in this.$store.state.standaloneFileDataByType">
    <template v-if="dataType === this.$store.state.selectedStandaloneDataFileType">
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

  <DataFileSelectTabs :file-data-by-type-mapping="this.$store.state.mappingFileDataByType"
                      :selected-data-file-type="this.$store.state.selectedMappingDataFileType"
                      @file-type-update-call="selectMappingDataFileType"
  />

  <template v-for="(dataValues, dataType, _) in this.$store.state.mappingFileDataByType">
    <template v-if="dataType === this.$store.state.selectedMappingDataFileType">
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
      <DataComposerOverview :dataFileObjArray="this.$store.state.selectedData"/>
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
            <pre id="template-content-display-1" v-html="this.$store.state.selectedDataJsonString"/>
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
            <template v-for="(request, index) in this.$store.state.selectedDataRequestSamples">
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

.row-container {
  margin: 3em;
}

.accordion.nested .accordion-header {
  margin-left: 1em;
}

.accordion .accordion-header {
  text-align: left;
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

</style>




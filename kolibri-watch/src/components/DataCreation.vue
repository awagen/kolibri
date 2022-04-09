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
  <DataComposerOverview :dataFileObjArray="this.$store.state.selectedData"/>


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

.divider {
  border-color: #353535;
}

</style>




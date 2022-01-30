<template>

  <h2 class="nodeListHeader">STORED DATA</h2>

  <DataFileSelectTabs/>

  <template v-for="(dataValues, dataType, _) in this.$store.state.fileDataByType">
    <template v-if="dataType === this.$store.state.selectedDataFileType">
      <DataFileOverview :show-add-button="true" :show-delete-button="false" :dataFileObjArray="dataValues" @addDataFile="addDataToComposer"/>
    </template>
  </template>

  <div class="divider"></div>

  <h2 class="nodeListHeader">COMPOSER</h2>
  <DataFileOverview :show-add-button="false" :show-delete-button="true" :dataFileObjArray="this.$store.state.selectedDataFiles" @removeDataFile="removeDataFromComposer"/>


</template>

<script>

import {onMounted} from "vue";
import DataFileSelectTabs from "../components/partials/DataFileSelectTabs.vue";
import DataFileOverview from "../components/partials/DataFileOverview.vue";

export default {

  props: [],
  components: {DataFileSelectTabs, DataFileOverview},
  methods: {
    selectDataFileType(fileType) {
      this.$store.commit("updateSelectedDataFileType", fileType)
    },
    addDataToComposer(fileObj){
      this.$store.commit("addSelectedDataFile", fileObj)
    },
    removeDataFromComposer(fileObj){
      this.$store.commit("removeSelectedDataFile", fileObj)
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




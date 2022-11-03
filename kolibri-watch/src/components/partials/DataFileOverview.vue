<template>

  <div class="row-container columns">
    <div class="col-12 col-sm-12 columns">
      <DataSampleInfo v-for="dataFile in dataFileObjArray" :show-delete-button="showDeleteButton"
                      @remove-data-func="removeData"
                      @add-data-file-func="addDataToComposer"
                      @add-data-to-mapping-func="addDataToComposerMapping"
                      :show-add-as-key-button="showAddAsKeyButton"
                      :show-add-button="showAddButton"
                      :data-sample-info="dataFile"
                      :mapping-info="null"/>
    </div>
  </div>

</template>

<script>

import {onMounted} from "vue";
import DataSampleInfo from "./DataSampleInfo.vue";

export default {
  props: [
      'dataFileObjArray',
      'showAddButton',
      'showDeleteButton',
      'showAddAsKeyButton',
      'isMappings'
  ],
  components: {DataSampleInfo},
  methods: {
    addDataToComposer(fileObj){
      if (this.isMappings) this.addDataToComposerMapping(fileObj)
      else this.addStandaloneDataToComposer(fileObj)
    },

    addStandaloneDataToComposer(fileObj){
      this.$store.commit("addSelectedDataFile", fileObj)
    },
    addDataToComposerMapping(fileObj){
      this.$store.commit("addSelectedDataToMapping", fileObj)
    },
    removeDataFromComposer(fileObj){
      this.$store.commit("removeSelectedDataFile", fileObj)
    },

    removeData({fileObj, mappingObj}){
      if (!fileObj.isMapping && mappingObj !== undefined && fileObj === mappingObj.data.keyValues) {
        this.removeMappingFromComposer(mappingObj)
      }
      else if (!fileObj.isMapping && mappingObj === undefined){
        this.removeDataFromComposer(fileObj)
      }
      else if (fileObj.isMapping){
        this.removeMappedValueFromComposerMapping(fileObj, mappingObj)
      }
    },
    removeMappingFromComposer(mappingObj){
      this.$store.commit("removeSelectedMapping", mappingObj)
    },
    removeMappedValueFromComposerMapping(fileObj, mappingObj){
      this.$store.commit("removeMappingFromMappedValues", {fileObj, mappingObj})
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

.popover button {
  background-color: #588274;
  border-width: 0;
  margin: 0.3em;
}

</style>
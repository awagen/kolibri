<template>

  <div class="row-container columns">
    <div class="col-12 col-sm-12 columns">
          <span class="composer-data-info-container" v-for="(dataFile, index) in dataFileObjArray">
            {{index}} )
            <DataSampleInfo v-if="dataFile.type === 'standalone'" :show-delete-button="true"
                            @remove-data-func="removeData"
                            @add-data-file-func="addDataToComposer"
                            @add-data-to-mapping-func="addDataToComposerMapping"
                            :show-add-as-key-button="false"
                            :show-add-button="false"
                            :data-sample-info="dataFile.data"
                            :mapping-info="null"/>
            <DataMappingInfo v-if="dataFile.type === 'mapping'"
                            :mapping-info="dataFile.data"/>
          </span>
    </div>
  </div>

</template>

<script>

import {onMounted} from "vue";
import DataSampleInfo from "./DataSampleInfo.vue";
import DataMappingInfo from "./DataMappingInfo.vue";

export default {
  props: [
    'dataFileObjArray',
    'showAddButton',
    'showDeleteButton',
    'showAddAsKeyButton',
    'isMappings'
  ],
  components: {DataMappingInfo, DataSampleInfo},
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

    removeData(fileObj, mappingObj){
      if (!fileObj.isMapping && mappingObj && fileObj === mappingObj.data.keyValues) {
        this.removeMappingFromComposer(mappingObj)
      }
      else if (!fileObj.isMapping && !mappingObj){
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

.composer-data-info-container {
  margin-top: 1em;
}

.row-container {
  margin: 3em;
}

.popover button {
  background-color: #588274;
  border-width: 0;
  margin: 0.3em;
}

</style>
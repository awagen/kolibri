<template>

  <div class="panel mapping-data-info-container">
    <div class="panel-header">
      <div class="panel-title">Mapping</div>
    </div>
    <div class="panel-body">
      <!-- contents -->

      <!-- first represent the key value. If that is deleted, the whole mapping shall be deleted -->
      <h3>Keys</h3>
      <DataSampleInfo :show-delete-button="true"
                      @remove-data-func="removeData"
                      :show-add-as-key-button="false"
                      :show-add-button="false"
                      :data-sample-info="mappingInfo.keyValues"
                      :mapping-info="mappingInfo"
                      :mapping-keys-index="0"/>

      <!-- display each mapped value together with its assigned mapping index -->
      <h3>Mappings</h3>
      <div v-for="(sample, index) in mappingInfo.mappedValues">
        <DataSampleInfo :show-delete-button="true"
                        @remove-data-func="removeData"
                        :show-add-as-key-button="false"
                        :show-add-button="false"
                        :data-sample-info="sample"
                        :mapping-info="mappingInfo"
                        :mapping-values-index="index + 1"/>
      </div>
    </div>
  </div>

</template>

<script>

import {onMounted} from "vue";
import DataSampleInfo from "./DataSampleInfo.vue";

export default {
  props: [
    'mappingInfo'
  ],
  components: {DataSampleInfo},
  methods: {
    removeDataFromComposer(fileObj){
      this.$store.commit("removeSelectedDataFile", fileObj)
    },

    removeData(fileObj, mappingObj){
      if (!fileObj.isMapping && mappingObj !== undefined && fileObj === mappingObj.keyValues) {
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

.panel .panel-body {
  overflow: visible;
}

.mapping-data-info-container {
  width: auto;
  padding-bottom: 1em;
}

.popover button {
  background-color: #588274;
  border-width: 0;
  margin: 0.3em;
}

.panel {
  display: inline-block;
}

</style>
<template>

  <span class="data-info-container">
    <span class="badge" :data-badge=dataSampleInfo.totalNrOfSamples>
      <a v-if="showDeleteButton" @click="this.$emit('removeDataFunc', dataSampleInfo, mappingInfo)" href="#" class="btn btn-clear"
         aria-label="Close" role="button"></a>
      <span v-if="mappingValuesIndex >= 0"> Index: {{mappingValuesIndex}} - </span>
      <span v-if="mappingKeysIndex >= 0 && !mappingValuesIndex"> Index: {{mappingKeysIndex}} - </span>
      {{ dataSampleInfo.identifier }}: {{ dataSampleInfo.fileName }}

      <!-- if available, display some field info here -->
      <div class="popover popover-right">
        <button class="btn btn-action s-circle"><i class="icon icon-message"></i></button>
        <div class="popover-container">
          <div class="card">
            <div class="card-header">
              <b>Sample Values</b>
            </div>
            <div class="card-body">
              <div v-for="(sample, index) in dataSampleInfo.samples">
                {{ index }}: {{ sample }}
              </div>
            </div>
            <div class="card-footer">
              <div>
                <b>Description</b> <br>
                {{ dataSampleInfo.description }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </span>

    <!-- if some mapping index is specified, the sample info reflects a mapping with editable mapping index -->
    <div v-if="mappingValuesIndex >= 0" class="form-group key-index">
      <label class="form-label" for="key-index-input">Mapped To Index:</label>
      <input class="form-input" @change="this.$store.commit('recalculateSelectedDataJsonString')" type="number" v-model="dataSampleInfo.mappedToIndex" id="key-index-input">
    </div>

    <div>
      <button v-if="showAddButton" @click="this.$emit('addDataFileFunc', dataSampleInfo)" class="btn btn-action k-add-button s-circle">
        <i class="icon icon-plus"></i>
      </button>
      <button v-if="showAddAsKeyButton" @click="this.$emit('addDataToMappingFunc', dataSampleInfo)" class="btn btn-action k-add-button s-circle">
        <i class="icon-key"/>
      </button>
    </div>
  </span>

</template>

<script>

import {onMounted} from "vue";

export default {
  props: [
    'dataSampleInfo',
    'mappingInfo',
    'showAddButton',
    'showDeleteButton',
    'showAddAsKeyButton',
    'removeDataFunc',
    'addDataFileFunc',
    'addDataToMappingFunc',
    'mappingKeysIndex',
    'mappingValuesIndex'
  ],
  methods: {},
  setup(props) {
    onMounted(() => {
    })
    return {}
  }
}

</script>

<style scoped>

.popover, .popover-right, .popover-container {
  overflow: visible;
}

.badge {
  margin: 1em;
}

.badge[data-badge]::after {
  background: #25333C;
  box-shadow: 0 0 0 0.1rem #588274;
  color: lightgrey;
  margin-left: 0.5em;
}

.popover button {
  background-color: #588274;
  border-width: 0;
  margin: 0.3em;
}

.card {
  white-space: break-spaces;
}

button.k-add-button {
  background-color: transparent;
  border-width: 0;
  color: white;
}

button.k-add-button:hover {
  background-color: #588274;
}

.icon-key {
  background-color: white;
  -webkit-mask-image: url("../../assets/images/key.svg");
  mask-image: url("../../assets/images/key.svg");
  height: 20px;
  width: 20px;
  display: inline-block;
  margin-left: 1em;
}

.form-group.key-index {
  margin: 0 auto;
  width: 10em;
}

.form-group.key-index input {
  margin: 0 auto;
  width: 4em;
}

</style>
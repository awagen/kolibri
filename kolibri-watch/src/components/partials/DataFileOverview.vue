<template>

  <div class="row-container columns">
    <div class="col-12 col-sm-12 columns">
          <span v-for="dataFile in dataFileObjArray">
            <span class="badge" :data-badge=dataFile.totalNrOfSamples>
            <!-- TODO: comment in in case some delete functionality shall be added -->
            <a v-if="showDeleteButton" @click="this.$emit('removeDataFile', dataFile)" href="#" class="btn btn-clear" aria-label="Close" role="button"></a>
            {{ dataFile.identifier }}: {{ dataFile.fileName }}

              <!-- if available, display some field info here -->
              <div class="popover popover-right">
                <button class="btn btn-action s-circle"><i class="icon icon-message"></i></button>
                <div class="popover-container">
                  <div class="card">
                    <div class="card-header">
                      <b>Sample Values</b>
                    </div>
                    <div class="card-body">
                      <div v-for="(sample, index) in dataFile.samples">
                        {{ index }}: {{ sample }}
                      </div>
                    </div>
                    <div class="card-footer">
                      <div>
                        <b>Description</b> <br>
                        {{ dataFile.description }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              </span>
              <div>
                <!-- emmit event to be handled by parent -->
                <button v-if="showAddButton" @click="this.$emit('addDataFile', dataFile)" class="btn btn-action k-add-button s-circle"><i class="icon icon-plus"></i></button>
              </div>
          </span>
    </div>
  </div>

</template>

<script>

import {onMounted} from "vue";

export default {
  props: [
      'dataFileObjArray',
      'showAddButton',
      'showDeleteButton'
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

.row-container {
  margin: 3em;
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

</style>
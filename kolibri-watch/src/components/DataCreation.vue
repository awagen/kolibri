<template>

  <div v-for="(dataValues, dataType, index) in this.$store.state.fileDataByType" class="row-container columns">
    <h3 class="title">
      {{dataType}}
    </h3>

    <!-- TODO: add the "add"-function (needs type, e.g FROM_FILE, BY_VALUES, name,... -->
    <!-- <span class="add-entry"><i class="icon icon-plus"></i></span> -->

    <div class="col-12 col-sm-12 columns">
      <span v-for="dataFile in dataValues" class="badge" :data-badge=dataFile.totalNrOfSamples>
        <a href="#" class="btn btn-clear" aria-label="Close" role="button"></a>
        {{dataFile.identifier}}: {{dataFile.fileName}}

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
                    {{index}}: {{ sample }}
                  </div>
                </div>
                <div class="card-footer">
                  <div>
                    <b>Description</b> <br>
                    {{dataFile.description}}
                  </div>
                </div>
              </div>
            </div>
          </div>
      </span>
    </div>
  </div>

  <div class="divider"></div>


</template>

<script>

import {onMounted} from "vue";

export default {

  props: [],
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
  box-shadow: 0 0 0 0.1rem grey;
  color: lightgrey;
  margin-left: 0.5em;
}

span.add-entry {
  font-size: 1.2em;
  margin-left: 1.5em;
  color: #588274;
}

.row-container .title {
  width: 6em;
  text-align: left;
  text-transform: lowercase;
}

.row-container .title::first-letter {
  text-transform: uppercase;
}

.divider {
  border-color: #353535;
}

.popover button {
  background-color: #588274;
  border-width: 0;
  margin:0.3em;
}

.card {
  white-space: break-spaces;
}


</style>




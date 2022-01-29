<template>

  <h2 class="nodeListHeader">STORED DATA</h2>

  <DataFileSelectTabs/>

  <template v-for="(dataValues, dataType, _) in this.$store.state.fileDataByType">
    <template v-if="dataType === this.$store.state.selectedDataFileType">
      <div class="row-container columns">
        <div class="col-12 col-sm-12 columns">
          <span v-for="dataFile in dataValues">
            <span class="badge" :data-badge=dataFile.totalNrOfSamples>
            <!-- TODO: comment in in case some delete functionality shall be added -->
            <!--        <a href="#" class="btn btn-clear" aria-label="Close" role="button"></a>-->
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
                <!-- TODO: using add button shall add the data samples to the composer.
                 Composer to be added below the stored data
                 -->
                <button @click="addDataToComposer(dataFile)" class="btn btn-action k-add-button s-circle"><i class="icon icon-plus"></i></button>
              </div>
          </span>
        </div>
      </div>
    </template>
  </template>

  <div class="divider"></div>

  <!-- composer can be hierarchical, meaning root array elements would be permutated across each other, while
   an element that itself is an array would represent a subgrouping where its n-th element is the n-th permutation
   of the elements contained in it (thus supplies multiple values)
   -->
  <h2 class="nodeListHeader">COMPOSER</h2>
  Coming shortly .. :)


</template>

<script>

import {onMounted} from "vue";
import DataFileSelectTabs from "../components/partials/DataFileSelectTabs.vue";

export default {

  props: [],
  components: {DataFileSelectTabs},
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
  margin: 0.3em;
}

.card {
  white-space: break-spaces;
}

.tab a .choice-title {
  /* need block style such that only uppercasing first char works */
  display: inline-block;
  text-transform: lowercase;
  color: #9C9C9C;
  margin-right: 1.5em;
}

.tab li {
  margin-right: 2em;
}

.tab-item.active .choice-title {
  color: black;
}

.tab-item.active a {
  border-bottom: none;
}

.tab a .choice-title::first-letter {
  text-transform: uppercase;
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




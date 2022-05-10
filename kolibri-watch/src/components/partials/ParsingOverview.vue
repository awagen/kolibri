<template>

  <!-- metric information / selection -->
  <div class="row-container">

    <div class="accordion col-12">

      <input type="checkbox" id="parsing-accordion" name="accordion-checkbox" hidden>

      <label class="accordion-header" for="parsing-accordion">
        <i class="icon icon-arrow-right mr-1"></i>
        <h2 class="k-title">Parsing</h2>
      </label>

      <div class="accordion-body metric">
        <div class="columns">
          <form class="form-horizontal col-6 column">
            <!-- here go the selectors -->
            <div class="form-group metric">
              <span>Parsing Selector</span>
            </div>
            <div class="form-group metric">
              <label class="form-inline form-label" for="parsing-name-input">Name:</label>
              <input id="parsing-name-input" class="form-input metric" type="text">
            </div>

            <div class="form-group metric">
              <label class="form-inline form-label" for="parsing-selector-input">Selector:</label>
              <input id="parsing-selector-input" class="form-input metric" type="text">
            </div>

            <div class="form-group metric">
              <label class="form-inline form-label" for="parsing-type-selector">Type:</label>
              <select id="parsing-type-selector" class="form-select">
                <option>Choose an option</option>
                <option>STRING</option>
                <option>DOUBLE</option>
                <option>FLOAT</option>
                <option>BOOLEAN</option>
              </select>
            </div>

            <button @click.prevent="addSelector()" class="btn btn-action k-add-button s-circle">
              <i class="icon icon-plus"></i>
            </button>

            <div class="divider"></div>
            <template v-for="selector in this.$store.state.parsingState.selectedParsingSelectors">
              <span class="chip k-chip">
                {{ selector.name }}
                <a @click.prevent="this.$store.commit('removeParsingSelectorFromSelected', selector.name)" href="#" class="btn btn-clear" aria-label="Close" role="button"></a>
              </span>
            </template>

          </form>

          <form class="form-horizontal col-6 column">
            <!-- here goes the json representation of the added selectors -->
            <pre id="metrics-content-display" v-html="this.$store.state.parsingState.selectedParsingSelectorsFormattedJsonString"/>
          </form>
        </div>

      </div>

    </div>

  </div>

</template>


<script>

import {onMounted} from "vue";

export default {

  props: [],
  components: {},
  methods: {

    addSelector(){
      let fieldName = document.getElementById("parsing-name-input").value
      let selector = document.getElementById("parsing-selector-input").value
      let fieldType = document.getElementById("parsing-type-selector").value
      this.$store.commit("addParsingSelector", {fieldName, selector, fieldType})
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
.accordion-body.metric {
  text-align: left;
}

.form-group.metric {
  display: inline-block;
  margin-left: 2em;
  width: 10em;
}

.form-input.metric {
  display: inline-block;
}

.row-container {
  margin: 3em;
}

.accordion.nested .accordion-header {
  margin-left: 1em;
}

.accordion .accordion-header {
  text-align: left;
}

button.k-add-button {
  background-color: transparent;
  border-width: 0;
  color: white;
}

button.k-add-button:hover {
  background-color: #588274;
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

h2.k-title {
  display: inline-block;
  padding-left: 1em;
}

.chip.k-chip {
  color:black;
  margin-top:.5em;
}
</style>
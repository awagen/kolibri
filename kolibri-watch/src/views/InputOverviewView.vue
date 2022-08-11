<template>
  <div class="form-container-experiment-create columns">
    <div class="form-horizontal col-12">
      <form class="form-horizontal col-6 column">
        <h3 class="k-title">
          Input Overview
        </h3>

        <div class="form-group">
          <div class="col-3 col-sm-12">
            <label class="form-label" for="1">intExample</label>
          </div>
          <div id="1" class="col-9 col-sm-12">
            <NumberStructDef
                @value-changed="valueChanged"
                :min="0"
                :max="10"
                name="intExample"
                value-type="INT"
                element_id="int1">
            </NumberStructDef>
          </div>
          <div class="k-form-separator"></div>
          <!-- select the needed template based on above selection -->
          <div class="col-3 col-sm-12">
            <label class="form-label" for="2">floatExample</label>
          </div>
          <div id="2" class="col-9 col-sm-12">
            <NumberStructDef
                @value-changed="valueChanged"
                :min="0.1"
                :max="0.9"
                name="floatExample"
                value-type="FLOAT"
                element-id="float1"></NumberStructDef>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script>

import NumberStructDef from "../components/partials/structs/NumberStructDef.vue";

export default {

  props: [
  ],
  components: {NumberStructDef},
  methods: {

    valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

  },
  setup(props) {
    return {
    }
  }

}

</script>

<style scoped>
</style>
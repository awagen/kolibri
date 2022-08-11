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
            <SingleValueStructDef
                @value-changed="valueChanged"
                name="intExample"
                :value-type=InputType.INT
                element_id="int1"
                :step="1"
                :validation-def="{'type': InputType.INT, 'min': 0, 'max': 5}">
            </SingleValueStructDef>
          </div>
          <div class="k-form-separator"></div>
          <!-- select the needed template based on above selection -->
          <div class="col-3 col-sm-12">
            <label class="form-label" for="2">floatExample</label>
          </div>
          <div id="2" class="col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                name="floatExample"
                :value-type=InputType.FLOAT
                element-id="float1"
                :step="0.1"
                :validation-def="{'type': InputType.FLOAT, 'min': 0.0, 'max': 0.6}">
            </SingleValueStructDef>
          </div>
        </div>
        <div class="form-group">
          <div class="col-3 col-sm-12">
            <label class="form-label" for="3">stringExample</label>
          </div>
          <div id="3" class="col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                name="stringExample"
                :value-type=InputType.STRING
                element-id="string1"
                :validation-def="{'type': InputType.STRING, 'regex': '^a\\w*$', 'max': 0.6}">
            </SingleValueStructDef>
          </div>
        </div>

      </form>
    </div>
  </div>
</template>

<script>

import SingleValueStructDef from "../components/partials/structs/SingleValueStructDef.vue";
import {InputType} from "../utils/dataValidationFunctions.ts"

export default {

  props: [
  ],
  components: {SingleValueStructDef},
  methods: {

    valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

  },
  setup(props) {
    return {
      InputType
    }
  }

}

</script>

<style scoped>
</style>
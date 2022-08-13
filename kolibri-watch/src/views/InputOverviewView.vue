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
          <div id="1" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new NumberInputDef(
                    'intExample',
                    'int1',
                    1,
                    0,
                    5
                )">
            </SingleValueStructDef>
          </div>
          <div class="k-form-separator"></div>
          <div class="col-3 col-sm-12">
            <label class="form-label" for="2">floatExample</label>
          </div>
          <div id="2" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new NumberInputDef(
                    'floatExample',
                    'float1',
                    0.1,
                    0.0,
                    0.5
                )">
            </SingleValueStructDef>
          </div>
        </div>
        <div class="form-group">
          <div class="col-3 col-sm-12">
            <label class="form-label" for="3">stringExample</label>
          </div>
          <div id="3" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new StringInputDef(
                    'stringExample',
                    'string1',
                    '^a\\w*$'
                )">
            </SingleValueStructDef>
          </div>
          <div class="col-3 col-sm-12">
            <label class="form-label" for="4">isOn</label>
          </div>
          <div id="4" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new BooleanInputDef(
                    'isOn',
                    'boolean1'
                )">
            </SingleValueStructDef>
          </div>
        </div>
        <!-- next form group -->
        <div class="form-group">
          <div class="col-3 col-sm-12">
            <label class="form-label" for="5">choiceExample</label>
          </div>
          <div id="5" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new ChoiceInputDef(
                    'choiceExample',
                    'choice1',
                    ['s1', 's2']
                )">
            </SingleValueStructDef>
          </div>
          <div class="col-3 col-sm-12">
            <label class="form-label" for="6">floatChoice1</label>
          </div>
          <div id="6" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="new FloatChoiceInputDef(
                    'floatChoiceExample',
                    'floatChoice1',
                    [0.021, 0.03],
                    0.00001
                )">
            </SingleValueStructDef>
          </div>
        </div>

      </form>
    </div>
  </div>
</template>

<script>

import SingleValueStructDef from "../components/partials/structs/SingleValueStructDef.vue";
import {InputType, StringInputDef, BooleanInputDef, NumberInputDef,
ChoiceInputDef, FloatChoiceInputDef} from "../utils/dataValidationFunctions.ts"

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
      InputType,
      NumberInputDef,
      StringInputDef,
      BooleanInputDef,
      ChoiceInputDef,
      FloatChoiceInputDef
    }
  }

}

</script>

<style scoped>

.k-input {
  text-align: left;
}

</style>
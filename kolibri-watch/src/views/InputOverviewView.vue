<template>
  <div class="form-container-experiment-create columns">
    <div class="form-horizontal col-12">
      <form class="form-horizontal col-6 column">
        <h3 class="k-title">
          Input Overview
        </h3>

        <NestedFieldSeqStructDef
          :fields="[
              new NumberInputDef('intExample',
                'int1',
                1,
                0,
                5
              ),
              new NumberInputDef(
                    'floatExample',
                    'float1',
                    0.1,
                    0.0,
                    0.5
                ),
              new StringInputDef(
                    'stringExample',
                    'string1',
                    '^a\\w*$'
                ),
              new BooleanInputDef(
                    'isOn',
                    'boolean1'
                ),
              new ChoiceInputDef(
                    'choiceExample',
                    'choice1',
                    ['s1', 's2']
                ),
              new FloatChoiceInputDef(
                    'floatChoiceExample',
                    'floatChoice1',
                    [0.021, 0.03],
                    0.00001
                )
          ]">
        </NestedFieldSeqStructDef>

      </form>
    </div>
  </div>
</template>

<script>

import NestedFieldSeqStructDef from "../components/partials/structs/NestedFieldSeqStructDef.vue";
import {InputType, StringInputDef, BooleanInputDef, NumberInputDef,
ChoiceInputDef, FloatChoiceInputDef} from "../utils/dataValidationFunctions.ts"

export default {

  props: [
  ],
  components: {NestedFieldSeqStructDef},
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

</style>
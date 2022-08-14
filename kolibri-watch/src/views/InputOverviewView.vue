<template>
  <div class="row-container columns">

    <form class="form-horizontal col-6 column">
      <h3 class="k-title">
        Input Overview
      </h3>

        <NestedFieldSeqStructDef
            @value-changed="valueChanged"
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
                  ),
                new SeqInputDef(
                    'seqExample',
                    'seq1',
                    new NumberInputDef(
                      'floatExample',
                      'float1',
                      0.1,
                      0.0,
                      0.5
                  )
                )
            ]">
        </NestedFieldSeqStructDef>

        <!-- now some generic sequence that can take an arbitrary nr of elements of the passed type -->
        <!--
        <GenericSeqStructDef
            @value-changed="valueChanged"
            name="inputSequence"
            :input-def="new NumberInputDef('intSeqExample',
                  'intSeq1',
                  1,
                  0,
                  5
                )">
        </GenericSeqStructDef>
        -->

    </form>

    <!-- json overview container -->
    <form class="form-horizontal col-6 column">
      <h3 class="k-title">
        JSON
      </h3>

      <div class="k-json-container col-12 col-sm-12">
        <pre id="template-content-display-1" v-html="this.$store.state.jobInputDefState.searchEvalJobDefJsonString"/>
      </div>

    </form>

    </div>
</template>

<script>

import NestedFieldSeqStructDef from "../components/partials/structs/NestedFieldSeqStructDef.vue";
import {InputType, StringInputDef, BooleanInputDef, NumberInputDef,
ChoiceInputDef, FloatChoiceInputDef, SeqInputDef} from "../utils/dataValidationFunctions.ts"
import GenericSeqStructDef from "../components/partials/structs/GenericSeqStructDef.vue";

export default {

  props: [
  ],
  components: {GenericSeqStructDef, NestedFieldSeqStructDef},
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
      FloatChoiceInputDef,
      SeqInputDef
    }
  }

}

</script>

<style scoped>

.row-container {
  margin: 3em;
}

pre#template-content-display-1 {
  margin-top: 2em;
}

.form-horizontal {
  padding: .4rem 0;
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

</style>
<template>
  <div class="row-container columns">

    <form class="form-horizontal col-6 column">
      <h3 class="k-title">
        Input Overview
      </h3>

      <NestedFieldSeqStructDef
          @value-changed="valueChanged"
          :conditional-fields="[
              new ConditionalFields(
                  'stringTest1',
                  new Map(
                      Object.entries({
                            'a': [new FieldDef(
                            'condIntTest1',
                                  new NumberInputDef(
                                    'condInt1',
                                    1,
                                    0,
                                    2
                                  ),
                          true
                          )]
                      })
                  )
              )
          ]"
          :fields="[
                new FieldDef(
                    'intTest1',
                new NumberInputDef(
                  'int1',
                  1,
                  0,
                  5
                ),
                true),
                new FieldDef(
                    'floatTest1',
                    new NumberInputDef(
                          'float1',
                          0.1,
                          0.0,
                          0.5
                    ),
                  true
                ),
                new FieldDef(
                      'stringTest1',
                      new StringInputDef(
                            'string1',
                            '^a\\w*$'
                      ),
              true
                ),
                new FieldDef(
                  'booleanTest1',
                  new BooleanInputDef(
                        'boolean1'
                    ),
              true
              ),
              new FieldDef(
                  'choiceTest1',
                  new ChoiceInputDef(
                        'choice1',
                        ['s1', 's2']
                    ),
                true
              ),
              new FieldDef(
                  'floatChoiceTest1',
                  new FloatChoiceInputDef(
                        'floatChoice1',
                        [0.021, 0.03],
                        0.00001
                    ),
                true
                ),
              new FieldDef(
                  'seqTest1',
                  new SeqInputDef(
                      'seq1',
                      new NumberInputDef(
                        'float2',
                        0.1,
                        0.0,
                        0.5
                    )
                  ),
                true
              ),
              new FieldDef(
                  'seqTest2',
                  new SeqInputDef(
                      'seq2',
                      new ChoiceInputDef(
                        'choiceSeq1',
                        ['s1', 's2', 's3', 's4']
                      )),
                  true
              ),
              new FieldDef(
                  'floatSeqTest1',
                  new SeqInputDef(
                      'floatSeq1',
                      new FloatChoiceInputDef(
                        'floatChoiceSeq1',
                        [0.1, 0.2, 0.3, 0.4]
                      )),
                  true
              ),
              new FieldDef(
                  'booleanSeqTest1',
                new SeqInputDef(
                    'booleanSeq1',
                    new BooleanInputDef(
                      'booleanSeq11'
                  )
                ),
                true
                ),
              new FieldDef(
                  'mapTest1',
                new MapInputDef(
                    'mapTestID1',
                  new KeyValueInputDef(
                      'keyValueMapTestID1',
                      new StringInputDef(
                              'mapStringKeyValue1',
                              '^b\\w*$'
                        ),
                      new StringInputDef(
                              'mapStringValue1',
                              '^a\\w*$'
                        ),
                      undefined,
                      undefined

                  )),
                true
                )
            ]">
      </NestedFieldSeqStructDef>

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
import {
  InputType, StringInputDef, BooleanInputDef, NumberInputDef,
  ChoiceInputDef, FloatChoiceInputDef, SeqInputDef, FieldDef, ConditionalFields, KeyValueInputDef,
  MapInputDef
} from "../utils/dataValidationFunctions.ts"
import GenericSeqStructDef from "../components/partials/structs/GenericSeqStructDef.vue";
import KeyValueStructDef from "../components/partials/structs/KeyValueStructDef.vue";

export default {

  props: [],
  components: {KeyValueStructDef, GenericSeqStructDef, NestedFieldSeqStructDef},
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
      SeqInputDef,
      FieldDef,
      KeyValueInputDef,
      MapInputDef,
      ConditionalFields
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
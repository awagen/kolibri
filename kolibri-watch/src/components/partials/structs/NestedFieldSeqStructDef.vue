<template>

  <template v-for="(field, index) in fields">
      <div class="form-group">

        <template v-if="(field.valueFormat instanceof SingleValueInputDef)">
          <div class="col-3 col-sm-12">
            <label class="form-label" :for="index">{{field.name}}</label>
          </div>
          <div :id="index" class="k-input col-9 col-sm-12">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :name="field.name"
                :element-def="field.valueFormat">
            </SingleValueStructDef>
          </div>
        </template>

        <template v-if="(field.valueFormat instanceof SeqInputDef)">
          <div class="col-3 col-sm-12">
            <label class="form-label" :for="index">{{field.name}}</label>
          </div>
          <div :id="index" class="k-input col-9 col-sm-12">
            <GenericSeqStructDef
                @value-changed="valueChanged"
                :name="field.name"
                :input-def="field.valueFormat.inputDef">
            </GenericSeqStructDef>
          </div>
        </template>

        <template v-if="(field.valueFormat instanceof MapInputDef)">
          <MapStructDef
              @value-changed="valueChanged"
              :name="field.name"
              :key-value-input-def="field.valueFormat.keyValueDef"
          >
          </MapStructDef>
        </template>

        <template v-if="(field.valueFormat instanceof KeyValueInputDef)">
            <KeyValueStructDef
                @value-changed="valueChanged"
                :name="field.name"
                :position="0"
                :key-input-def="field.valueFormat.keyFormat"
                :value-input-def="field.valueFormat.valueFormat"
                :key-value="field.valueFormat.keyValue"
                >
            </KeyValueStructDef>
        </template>

        <div class="k-form-separator"></div>
      </div>
  </template>

</template>

<script>
import {SingleValueInputDef, SeqInputDef, KeyValuePairInputDef, KeyValueInputDef, MapInputDef} from "../../../utils/dataValidationFunctions.ts";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {ref} from "vue";
import { useStore } from 'vuex';
import GenericSeqStructDef from "./GenericSeqStructDef.vue";
import KeyValueStructDef from "./KeyValueStructDef.vue";
import MapStructDef from "./MapStructDef.vue";

export default {

  props: {
    // NOTE: more specific typing of array type doesnt seem to
    // work also when using PropType
    fields: {type: Array, required: true}
  },
  emits: ['valueChanged'],
  components: {GenericSeqStructDef, SingleValueStructDef, KeyValueStructDef, MapStructDef},
  computed: {
  },
  methods: {
  },
  setup(props, context) {
    const store = useStore()
    let fieldStates = ref({})
    props.fields.forEach(field => {
      fieldStates.value[field.name] = undefined
    })

    function valueChanged(attributes) {
      console.debug(`Incoming value changed event: ${JSON.stringify(attributes)}`)
      fieldStates.value[attributes.name] = attributes.value
      store.commit("updateSearchEvalJobDefState", fieldStates.value)
    }

    return {
      SingleValueInputDef,
      valueChanged,
      SeqInputDef,
      KeyValuePairInputDef,
      KeyValueInputDef,
      MapInputDef
    }
  }

}

</script>

<style scoped>

.k-input {
  text-align: left;
}

.k-form-separator {
  height: 2.8em;
}

</style>



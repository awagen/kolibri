<template>

  <template v-for="(field, index) in fields">
    <template v-if="(field instanceof SingleValueInputDef)">
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" :for="field.elementId-index">{{field.name}}</label>
        </div>
        <div :id="field.elementId-index" class="k-input col-9 col-sm-12">
          <SingleValueStructDef
              @value-changed="valueChanged"
              :element-def="field">
          </SingleValueStructDef>
        </div>
        <div class="k-form-separator"></div>
      </div>
    </template>
  </template>

</template>

<script>
import {SingleValueInputDef} from "../../../utils/dataValidationFunctions.ts";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {ref} from "vue";
import { useStore } from 'vuex';

export default {

  props: {
    // NOTE: more specific typing of array type doesnt seem to
    // work also when using PropType
    fields: {type: Array, required: true}
  },
  components: {SingleValueStructDef},
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
      // could rewrite to pass the refs from here and update in child, then
      // wed only need single mapping created once and updates are automatic
      fieldStates.value[attributes.name] = attributes.value
      // displayedJsonHtml.value = fieldsToJsonSyntaxHighlightedHtml()
      store.commit("updateSearchEvalJobDefState", fieldStates.value)
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

    return {
      SingleValueInputDef,
      valueChanged,
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



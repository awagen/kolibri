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

export default {

  props: {
    // NOTE: more specific typing of array type doesnt seem to
    // work also when using PropType
    fields: {type: Array, required: true}
  },
  components: {SingleValueStructDef},
  methods: {

    valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

  },
  setup(props) {
    return {
      SingleValueInputDef
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



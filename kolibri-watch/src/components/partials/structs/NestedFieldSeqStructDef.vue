<template>

  <form class="form-horizontal col-6 column">
    <h3 class="k-title">
      Input Overview
    </h3>

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
  </form>

  <!-- json overview container -->
  <form class="form-horizontal col-6 column">
    <h3 class="k-title">
      JSON
    </h3>

    <div class="k-json-container col-12 col-sm-12">
      <pre id="template-content-display-1" v-html="displayedJsonHtml"/>
    </div>

  </form>

</template>

<script>
import {SingleValueInputDef} from "../../../utils/dataValidationFunctions.ts";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {ref} from "vue";
import {objectToJsonStringAndSyntaxHighlight, stringifyObj} from "../../../utils/formatFunctions";

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
    let fieldStates = ref({})
    let displayedJsonHtml = ref("")
    props.fields.forEach(field => {
      fieldStates.value[field.name] = undefined
    })

    function valueChanged(attributes) {
      // could rewrite to pass the refs from here and update in child, then
      // wed only need single mapping created once and updates are automatic
      fieldStates.value[attributes.name] = attributes.value
      displayedJsonHtml.value = fieldsToJsonSyntaxHighlightedHtml()
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

    function fieldsToJson() {
      return stringifyObj(fieldStates.value)
    }

    function fieldsToJsonSyntaxHighlightedHtml() {
      return objectToJsonStringAndSyntaxHighlight(fieldStates.value)
    }

    return {
      SingleValueInputDef,
      valueChanged,
      displayedJsonHtml
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

pre#template-content-display-1 {

  margin-top: 2em;

}

.form-horizontal {
  padding: .4rem 0;
}

.form-horizontal .form-group {
  display: -ms-flexbox;
  display: flex;
  -ms-flex-wrap: wrap;
  flex-wrap: wrap;
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



<template>
  <div class="row-container columns">

    <form class="form-horizontal col-6 column">
      <h3 class="k-title">
        JobDefinition
      </h3>

      <NestedFieldSeqStructDef
          @value-changed="valueChanged"
          :conditional-fields="jobDef.conditionalFields"
          :fields="jobDef.fields"
          :is-root="true"
      >
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
import json from "../../tests/testdata/searchEvaluationJobStructDef.json"
import {ref} from "vue";
import {objToInputDef} from "@/utils/inputDefConversions";

export default {

  props: [],
  components: {NestedFieldSeqStructDef},
  methods: {

    valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

  },
  setup(props) {

    console.info("json def")
    console.log(json)
    let jobDef = ref({})
    jobDef.value = objToInputDef(
        json,
        "root",
        0
    )

    return {
      jobDef
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
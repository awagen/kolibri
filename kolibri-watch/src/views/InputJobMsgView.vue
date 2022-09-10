<template>
  <div class="row-container columns">

    <form class="form-horizontal col-8 column">
      <h3 class="k-title">
        JobDefinition
      </h3>

      <NestedFieldSeqStructDef
          @value-changed="valueChanged"
          :conditional-fields="this.$store.state.jobInputDefState.searchEvalInputDef.conditionalFields"
          :fields="this.$store.state.jobInputDefState.searchEvalInputDef.fields"
          :is-root="true"
      >
      </NestedFieldSeqStructDef>

    </form>

    <!-- json overview container -->
    <form class="form-horizontal col-4 column">
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

export default {

  props: [],
  components: {NestedFieldSeqStructDef},
  methods: {

    valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

  },
  setup(props) {
    return {}
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

.k-json-container {
  overflow: scroll;
}

</style>
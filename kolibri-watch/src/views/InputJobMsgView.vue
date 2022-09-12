<template>

  <!-- make jobs selectable and then display below definition content dependent upon selection -->
  <div class="form-group">
    <div class="col-3 col-sm-12">
      <label class="form-label" for="job-name-1">Select Job Name</label>
    </div>
    <div class="col-9 col-sm-12">
      <select @change="jobNameSelectEvent($event)" class="form-select k-value-selector" id="job-name-1">
        <option>Choose an option</option>
        <option v-for="jobName in Object.keys(this.$store.state.jobInputDefState.jobNameToInputDef)">{{ jobName }}</option>
      </select>
    </div>
  </div>

  <template v-if="selectedJobName !== undefined || selectedJobName ===''">
    <div class="row-container columns">

      <form class="form-horizontal col-8 column">
        <h3 class="k-title">
          Job: {{selectedJobName}}
        </h3>
        <h3 class="k-description">
          {{selectedJobDescription}}
        </h3>

        <NestedFieldSeqStructDef
            @value-changed="valueChanged"
            :conditional-fields="selectedJobDef.conditionalFields"
            :fields="selectedJobDef.fields"
            :is-root="false"
        >
        </NestedFieldSeqStructDef>

      </form>

      <!-- json overview container -->
      <form class="form-horizontal col-4 column">
        <h3 class="k-title">
          JSON
        </h3>

        <div class="k-json-container col-12 col-sm-12">
          <pre id="template-content-display-1" v-html="jobDefStateJsonString"/>
        </div>

      </form>

    </div>
  </template>
</template>

<script>

import NestedFieldSeqStructDef from "../components/partials/structs/NestedFieldSeqStructDef.vue";
import {ref} from "vue";
import {objectToJsonStringAndSyntaxHighlight} from "@/utils/formatFunctions";

export default {

  props: {
    selectedJobName: {type: String, required: false},
    selectedJobDef: {type: Object, required: false},
    selectedJobDescription: {type: String, required: false},
    selectedJobEndpoint: {type: String, required: false}
  },
  components: {NestedFieldSeqStructDef},
  methods: {},
  setup(props, context) {
    // let selectedJobName = ref("")
    // let selectedJobDescription = ref("")
    // let selectedJobDef = ref({})
    // let selectedJobEndpoint = ref("")
    let jobDefState = ref({})
    let jobDefStateJsonString = ref("")

    function jobNameSelectEvent(event) {
      props.selectedJobName = event.target.value
      // TODO: access to store doesnt work here ... migth wanna manage globally the state edited
      // so far for current selection, forgetting the previous ones (or map by name)
      props.selectedJobDef = this.store.state.jobInputDefState.jobNameToInputDef[selectedJobName]
      props.selectedJobDescription = this.store.state.jobInputDefState.jobNameToDescription[selectedJobName]
      props.selectedJobEndpoint = this.store.state.jobInputDefState.jobNameToEndpoint[selectedJobName]
    }

    function valueChanged(attributes) {
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
      let value = attributes.combinedValue
      jobDefState = value
      jobDefStateJsonString = objectToJsonStringAndSyntaxHighlight(jobDefState)
    }

    return {
      jobDefStateJsonString,
      jobNameSelectEvent,
      valueChanged
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

.k-json-container {
  overflow: scroll;
}

.k-value-selector {
  color: black;
}

</style>
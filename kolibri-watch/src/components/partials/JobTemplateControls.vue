<template>

  <!-- Display options to save the template and to sent it to the
  endpoint specified for the selected job -->
  <div class="form-separator"></div>
  <div class="form-group k-action-buttons">
    <!-- template save button -->
    <div class="form-separator"></div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionsAndSaveTemplate()" class="k-form k-full btn btn-action"
              id="save-template-1">
        SAVE TEMPLATE
      </button>
    </div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionAndExecuteJob()" class="k-form k-full btn btn-action"
              id="run-template-1">
        RUN TEMPLATE
      </button>
    </div>
  </div>

  <!-- Define the new file name to store the edit to -->
  <div class="form-separator"></div>
  <div class="form-group k-file-input">
    <div class="col-3 col-sm-12">
      <label class="form-label" for="template-edit-saveto-filename-1">New template filename</label>
    </div>
    <div class="col-9 col-sm-12">
      <input class="form-input k-value-selector" type="text" id="template-edit-saveto-filename-1"
             placeholder="New template filename">
    </div>
  </div>

</template>

<script>
import {postAgainstEndpoint, saveTemplate} from "@/utils/retrievalFunctions";

export default {

  props: {},
  components: {},
  methods: {

    /**
     * Retrieve the target file name to store to from input field
     * and then store for the currently selected job the current
     * edit state.
     * TOOD: do server side template validation against input definition
     * for that particular job and display successful store / error
     * messages
     */
    getSelectionsAndSaveTemplate() {
      console.info("saving template")
      let relativeFileName = document.getElementById("template-edit-saveto-filename-1").value
      let jobName = this.$store.state.jobInputDefState.selectedJobName
      if (relativeFileName === "") {
        console.info("empty relative file name, not sending for storage")
      }
      let templateContent = this.$store.state.jobInputDefState.jobNameToInputStatesObj[jobName]
      saveTemplate(jobName, relativeFileName, templateContent)
    },

    getSelectionAndExecuteJob() {
      let templateContent = this.$store.state.jobInputDefState.jobNameToInputStatesObj[this.$store.state.jobInputDefState.selectedJobName]
      // TODO: as in the above display success / error message here
      let jobName = this.$store.state.jobInputDefState.selectedJobName
      let jobEndpoint = this.$store.state.jobInputDefState.jobNameToEndpoint[jobName]
      console.info(`posting content for job endpoint '${jobEndpoint}'`)
      postAgainstEndpoint(jobEndpoint, templateContent)
    }

  },
  computed: {},
  setup(props, context) {
    return {}
  }

}
</script>

<style scoped>

.k-form.btn {
  padding: 0;
  margin: 0;
  display: block;
  background-color: #9999;
  color: black;
  border-width: 0;
}

.k-full.btn {
  width: 98%;
  margin-left: 1em;
  margin-right: 1em;
}

input#template-edit-saveto-filename-1 {
  padding-right: 1em;
  width: 98%;
}

button#save-template-1 {
  background-color: darkgreen;
}

button#run-template-1 {
  background-color: orange;
}

.k-action-buttons {
  margin: 1em;
}

.k-file-input {
  margin: 1em;
}

</style>
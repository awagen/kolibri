<template>

  <!-- Display options to save the template and to sent it to the
  endpoint specified for the selected job -->
  <div class="form-separator"></div>
  <div class="form-group k-action-buttons">
    <!-- template save button -->
    <div class="form-separator"></div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionsAndSaveTemplate" class="k-form k-full btn btn-action"
              id="save-template-1">
        SAVE TEMPLATE
      </button>
    </div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionAndExecuteJob" class="k-form k-full btn btn-action"
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

  <ResponseModal
    :show="showModal"
    :mode="mode"
    @responseModalClosed="responseModalClosedHandler"
    @responseModalOpened="responseModalOpenedHandler"
    :modal-title="modalTitle"
    :main-content="mainContent"
    :footer-content="footerContent"
    :fade-out-ok='true'
  >
  </ResponseModal>

</template>

<script>
import {postAgainstEndpoint, saveTemplate} from "../../utils/retrievalFunctions";
import ResponseModal from "../partials/ResponseModal.vue";
import {ref} from "vue";
import {useStore} from "vuex";

export default {

  props: {},
  components: {ResponseModal},
  methods: {
  },
  computed: {},
  setup(props, context) {
    const store = useStore()

    let showModal = ref(false)
    let modalTitle = ref("")
    let mainContent = ref("")
    let footerContent = ref("")
    let mode = ref("k-success")

    function responseModalClosedHandler() {
      showModal.value = false
    }

    function responseModalOpenedHandler() {
      showModal.value = true
    }

    function prepareOKResponseShow() {
      modalTitle.value = ""
      mainContent.value = ""
      mode.value = "k-success"
    }

    function prepareOKResponseShowAndShow() {
      prepareOKResponseShow()
      showModal.value = true
    }

    function prepareErrorResponseShow(title, description) {
      modalTitle.value = title
      mainContent.value = description
      mode.value = "k-fail"
    }

    function prepareErrorResponseShowAndShow(title, description) {
      prepareErrorResponseShow(title, description)
      showModal.value = true
    }

    /**
     * Retrieve the target file name to store to from input field
     * and then store for the currently selected job the current
     * edit state.
     * TOOD: do server side template validation against input definition
     * for that particular job and display successful store / error
     * messages
     */
    async function getSelectionsAndSaveTemplate() {
      let relativeFileName = document.getElementById("template-edit-saveto-filename-1").value
      let jobName = store.state.jobInputDefState.selectedJobName
      if (relativeFileName === "") {
        prepareErrorResponseShowAndShow("Persist Fail", "empty relative file name, not sending for storage")
        return
      }
      let templateContent = store.state.jobInputDefState.jobNameToInputStatesObj[jobName]
      let saveResult = await saveTemplate(jobName, relativeFileName, templateContent)
      if (saveResult.success) {
        prepareOKResponseShowAndShow()
      }
      else {
        prepareErrorResponseShowAndShow("Persist Fail", saveResult.msg)
      }
    }

    async function getSelectionAndExecuteJob() {
      let templateContent = store.state.jobInputDefState.jobNameToInputStatesObj[store.state.jobInputDefState.selectedJobName]
      let jobName = store.state.jobInputDefState.selectedJobName
      let jobEndpoint = store.state.jobInputDefState.jobNameToEndpoint[jobName]
      if (jobEndpoint == null || jobEndpoint.trim() === '') {
        prepareErrorResponseShowAndShow("Job Posting Fail", `job endpoint '${jobEndpoint}' not valid`)
        return;
      }
      if (templateContent == null || Object.keys(templateContent).length === 0) {
        prepareErrorResponseShowAndShow("Job Posting Fail", `template content '${templateContent}' not valid`)
        return;
      }
      let postResult = await postAgainstEndpoint(jobEndpoint, templateContent)
      if (postResult.success) {
        prepareOKResponseShowAndShow()
      }
      else {
        prepareErrorResponseShowAndShow("Job Posting Fail", postResult.msg)
      }
    }

    return {
      showModal,
      getSelectionsAndSaveTemplate,
      getSelectionAndExecuteJob,
      responseModalClosedHandler,
      responseModalOpenedHandler,
      modalTitle,
      mainContent,
      footerContent,
      mode
    }
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
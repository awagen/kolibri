<template>

  <!-- Display options to save the template and to sent it to the
  endpoint specified for the selected job -->
  <div class="form-separator"></div>
  <div class="form-group k-action-buttons">
    <!-- template save button -->
    <div class="form-separator"></div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionsAndSaveTemplate" class="k-form k-full btn btn-action save"
              :id="'save-template-' + pageId">
        SAVE TEMPLATE
      </button>
    </div>
    <div class="col-6 col-sm-12">
      <button type='button' @click="getSelectionAndExecuteJob" class="k-form k-full btn btn-action execute"
              :id="'run-template-' + pageId">
        RUN TEMPLATE
      </button>
    </div>
  </div>

  <!-- Define the new file name to store the edit to -->
  <div class="form-separator"></div>
  <div class="form-group k-file-input">
    <div class="col-3 col-sm-12">
      <label class="form-label" :for="'template-edit-saveto-filename-' + pageId">New template filename</label>
    </div>
    <div class="col-9 col-sm-12">
      <input class="form-input k-value-selector template-name" type="text" :id="'template-edit-saveto-filename-' + pageId"
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

  props: {
    pageId: {
      type: String,
      required: true,
      default: "1",
      description: "Id appended to the id-selectors used in this component.",
      validator(value) {
        return ["freeEdit", "byStruct"].includes(value)
      }
    }
  },
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

    function getJobName(){
      if (props.pageId === "freeEdit") {
        return store.state.templateState.selectedTemplateType
      }
      else if (props.pageId === "byStruct") {
        return store.state.jobInputDefState.selectedJobName
      }
      else {
        throw "Unknown pageId"
      }
    }

    function getTemplateContent(jobName){
      if (props.pageId === "freeEdit") {
        return store.state.templateState.selectedTemplateContent
      }
      else if (props.pageId === "byStruct") {
        return store.state.jobInputDefState.jobNameToInputStatesObj[jobName]
      }
      else {
        throw "Unknown pageId"
      }
    }

    function getJobEndpoint(jobName){
      if (props.pageId === "freeEdit") {
        return import.meta.env.VITE_KOLIBRI_TEMPLATE_EXECUTE_PATH
      }
      else if (props.pageId === "byStruct") {
        return store.state.jobInputDefState.jobNameToEndpoint[jobName]
      }
      else {
        throw "Unknown pageId"
      }
    }

    /**
     * Retrieve the target file name to store to from input field
     * and then store for the currently selected job the current
     * edit state.
     */
    async function getSelectionsAndSaveTemplate() {
      let fileNameId = "template-edit-saveto-filename-" + props.pageId
      let relativeFileName = document.getElementById(fileNameId).value
      let jobName = getJobName()
      if (relativeFileName === "") {
        prepareErrorResponseShowAndShow("Persist Fail", "empty relative file name, not sending for storage")
        return
      }
      let templateContent = getTemplateContent(jobName)
      let saveResult = await saveTemplate(jobName, relativeFileName, templateContent)
      if (saveResult.success) {
        prepareOKResponseShowAndShow()
      }
      else {
        prepareErrorResponseShowAndShow("Persist Fail", saveResult.msg)
      }
    }

    async function getSelectionAndExecuteJob() {
      let jobName = getJobName()
      let templateContent = getTemplateContent(jobName)
      let jobEndpoint = getJobEndpoint(jobName)
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

input.template-name {
  padding-right: 1em;
  width: 98%;
}

button.save {
  background-color: darkgreen !important;
}

button.execute {
  background-color: orange !important;
}

.k-action-buttons {
  margin: 1em;
}

.k-file-input {
  margin: 1em;
}

</style>
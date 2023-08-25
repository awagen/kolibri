<template>

  <!-- Display options to save the template and to sent it to the
  endpoint specified for the selected job -->
  <div class="form-separator"></div>

  <div class="form-group k-action-buttons">

    <!-- template save button -->
    <div class="form-separator"></div>
    <div class="col-6 col-sm-12">
      <Button
        emitted-event-name="saveTemplate"
        @save-template="getSelectionsAndSaveTemplate"
        button-class="save"
        :button-id="'save-template-' + pageId"
        title="SAVE TEMPLATE"
      >
      </Button>

    </div>
    <!-- template run button -->
    <div class="col-6 col-sm-12">
      <Button
          emitted-event-name="runTemplate"
          @run-template="getSelectionAndExecuteJob"
          button-class="execute"
          :button-id="'run-template-' + pageId"
          title="RUN TEMPLATE"
      >
      </Button>
    </div>
  </div>

  <!-- Define the new file name to store the edit to -->
  <div class="form-separator"></div>
  <div class="form-group k-file-input">
    <!-- Input field definition -->
    <Input
        :input-id="'template-edit-saveto-filename-' + pageId"
        label="New template filename"
        placeHolder="New template filename"
    ></Input>
  </div>

  <!-- Modal to indicate whether actions were successful or failed -->
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
import {postAgainstEndpoint, saveTemplate} from "@/utils/retrievalFunctions";
import ResponseModal from "../partials/ResponseModal.vue";
import {ref} from "vue";
import {useStore} from "vuex";
import Input from "@/components/partials/controls/Input.vue";
import Button from "@/components/partials/controls/Button.vue";

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
  components: {Button, Input, ResponseModal},
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

.k-action-buttons {
  margin: 1em;
}

.k-file-input {
  margin: 1em;
}

</style>
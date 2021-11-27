<template>
  <div class="form-container-experiment-create columns">
    <form class="form-horizontal col-6 column">
      <h3 class="title">
        Template Edit
      </h3>

      <!-- dropdown button group -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-type-1">Select Template Type</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="jobTypeSelectEvent($event)" class="form-select k-value-selector" id="template-type-1">
            <option>Choose an option</option>
            <option v-for="templateType in templateTypes">{{templateType}}</option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Template</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="templateSelectEvent($event)" class="form-select k-field k-value-selector" id="template-name-1">
            <option>Choose an option</option>
            <option v-for="templateName in templateNames">{{ templateName }}</option>
          </select>
        </div>
      </div>

      <!-- Selector for field names in json which will open an edit window for that specific field with apply option -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-field-1">Select Template Field</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="jobTemplateFieldEditSelectEvent($event)" class="form-select k-value-selector" id="template-field-1">
            <option>Choose an option (or edit freely)</option>
            <option v-for="fieldName in Object.keys(selectedTemplateContent)">{{fieldName}}</option>
          </select>
        </div>
      </div>

      <!-- Text area for template edit -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-edit-1">Replace Template Content</label>
          <!-- if available, display some field info here -->
          <div v-if="fieldInfoAvailable" class="popover popover-right">
            <button class="btn btn-action s-circle"><i class="icon icon-message"></i></button>
            <div class="popover-container">
              <div class="card">
                <div class="card-header">
                  <b>Field: {{selectedTemplateField}}</b>
                </div>
                <div class="card-body">
                  {{selectedTemplateFieldInfo}}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="col-9 col-sm-12">
          <textarea spellcheck="false" class="form-input k-area" id="template-edit-1" placeholder="Template Content"
                    rows="20">
          </textarea>
        </div>

        <!-- change apply button -->
        <div class="form-separator"></div>
        <div class="col-3 col-sm-12"></div>
        <div class="col-9 col-sm-12">
          <button type='button' @click="applyChanges()" class="k-form k-half k-right-padded btn btn-action" id="apply-changes-1">
            APPLY CHANGES
          </button>
        </div>
      </div>
    </form>

    <!-- Other half is the status display for stuff already added -->
    <form class="form-horizontal col-6 column k-json-panel">
      <h3 class="title">
        Resulting Json
      </h3>

      <div class="form-group">
        <!-- template save button -->
        <div class="form-separator"></div>
        <div class="col-6 col-sm-12">
          <button type='button' @click="saveTemplate()" class="k-form k-full btn btn-action" id="save-template-1">
            SAVE TEMPLATE
          </button>
        </div>
        <div class="col-6 col-sm-12">
          <button type='button' @click="runTemplate()" class="k-form k-full btn btn-action" id="run-template-1">
            RUN TEMPLATE
          </button>
        </div>
      </div>

      <!-- Define the new file name to store the edit to -->
      <div class="form-separator"></div>
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-edit-saveto-filename-1">New template filename</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input k-value-selector" type="text" id="template-edit-saveto-filename-1"
                 placeholder="New template filename">
        </div>
      </div>

      <div class="form-separator"></div>
      <pre id="template-content-display-1" v-html="selectedTemplateJsonString"/>
    </form>
  </div>
</template>

<script>
import {onMounted, ref} from "vue";
import {objectToJsonStringAndSyntaxHighlight, stringifyObj, baseJsonFormatting} from "../utils/formatFunctions";
import axios from "axios";

export default {

  props: [],
  setup(props) {
    // the names of template types for which specific templates can be requested
    const templateTypes = ref([])
    // url to request available template types
    const getTemplateTypesURL = "http://localhost:8000/templates/jobs/types"
    // the names of the available templates as retrieved via the templates url
    const templateNames = ref([])
    // url to retrieve the templates overview from. This endpoint provides available templates that can be retrieved
    // then via the getTemplateContentURL
    const getTemplatesURL = "http://localhost:8000/templates/jobs/overview"
    // url to retrieve template content from
    const getTemplateContentURL = "http://localhost:8000/templates/jobs"
    // template field selected for edit
    const selectedTemplateField = ref("")
    // boolean to indicate whether any field info is available for the selected field
    const fieldInfoAvailable = ref(false)
    // the actual current value for the selectedTemplateField
    const selectedTemplateFieldValue = ref("")
    // the info/description (if available) for the selectedTemplateField
    const selectedTemplateFieldInfo = ref("")
    // the partial selected for editing, e.g {selectedTemplateField: selectedTemplateFieldValue}
    const selectedTemplateFieldPartial = ref({})
    // selected type of template
    const selectedTemplateType = ref("")
    // selected name of template
    const selectedTemplateName = ref("")
    // the retrieved template content
    const selectedTemplateContent = ref("")
    // description per field for the retrieved content
    const selectedTemplateContentInfo = ref("")
    // the stringified values of the retrieved json
    const selectedTemplateJsonString = ref("")
    // the changes applied to the selected template
    const changedTemplateParts = ref({})
    // mapping of job type to execution url (then only post the json content with below jsonContentHeader and job should start)
    const jobTypeToExecutionUrlMapping = {
      "search-evaluation": "http://localhost:8000/search_eval_no_ser"
    }
    // header to post with the json
    const jsonContentHeader = "Content-Type: application/json"

    function jobTypeSelectEvent(event) {
      selectedTemplateType.value = event.target.value
      retrieveTemplatesForType(selectedTemplateType.value)
    }

    function templateSelectEvent(event) {
      selectedTemplateName.value = event.target.value
      retrieveTemplateContent(selectedTemplateType.value, selectedTemplateName.value)
    }

    function jobTemplateFieldEditSelectEvent(event) {
      selectedTemplateFieldPartial.value = {}
      selectedTemplateField.value = event.target.value
      // load the current field value for the selected field and the info (if any is provided)
      selectedTemplateFieldValue.value = selectedTemplateContent.value[selectedTemplateField.value]
      selectedTemplateFieldInfo.value = selectedTemplateContentInfo.value[selectedTemplateField.value]
      fieldInfoAvailable.value = selectedTemplateFieldInfo.value != null
      selectedTemplateFieldPartial.value[selectedTemplateField.value] = selectedTemplateFieldValue.value
      document.getElementById("template-edit-1").value = baseJsonFormatting(selectedTemplateFieldPartial.value)
    }

    function retrieveTemplateTypes() {
      return axios
          .get(getTemplateTypesURL)
          .then(response => {
            templateTypes.value = response.data
          }).catch(_ => {
            templateTypes.value = []
          })
    }

    function retrieveTemplatesForType(typeName) {
      return axios
          .get(getTemplatesURL + "?type=" + typeName)
          .then(response => {
            templateNames.value = response.data
          }).catch(_ => {
            templateNames.value = []
          })
    }

    function retrieveTemplateContent(typeName, templateName) {
      return axios
          .get(getTemplateContentURL + "?type=" + typeName + "&identifier=" + templateName)
          .then(response => {
            selectedTemplateContent.value = response.data["template"]
            selectedTemplateContentInfo.value = response.data["info"]
            selectedTemplateJsonString.value = objectToJsonStringAndSyntaxHighlight(selectedTemplateContent.value)
          }).catch(_ => {
            selectedTemplateContent.value = "";
            selectedTemplateJsonString.value = "";
          })
    }

    function applyChanges() {
      let changes = document.getElementById("template-edit-1").value
      changedTemplateParts.value = JSON.parse(changes)
      selectedTemplateContent.value = Object.assign({}, JSON.parse(stringifyObj(selectedTemplateContent.value)), changedTemplateParts.value);
      selectedTemplateJsonString.value = objectToJsonStringAndSyntaxHighlight(selectedTemplateContent.value)
    }

    onMounted(() => {
      retrieveTemplateTypes();
    })

    return {
      templateTypes,
      templateNames,
      jobTypeSelectEvent,
      templateSelectEvent,
      jobTemplateFieldEditSelectEvent,
      selectedTemplateType,
      selectedTemplateContent,
      selectedTemplateJsonString,
      changedTemplateParts,
      applyChanges,
      selectedTemplateField,
      selectedTemplateFieldValue,
      selectedTemplateFieldInfo,
      selectedTemplateFieldPartial,
      fieldInfoAvailable
    }
  },


}

</script>

<style scoped>

.k-field {
  background-color: #d3d3d3;
}

.k-area {
  background-color: #25333c;
}

.k-form-separator {
  height: 2.8em;
}

.form-container-experiment-create {
  margin-top: 3em;
}

.column {
  padding: .4rem 0 0;
}

.title {
  margin-bottom: 1em;
  text-align: center;
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

textarea#template-edit-1 {

  color: #9c9c9c;;

}

textarea.form-input:focus {

  border-color: white;

}

.k-form.btn {
  padding: 0;
  margin: 0;
  display: block;
  background-color: #9999;
  color: black;
  border-width: 0;
}

.k-half.btn {
  width: 50%;
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

pre#template-content-display-1 {

  margin-top: 2em;

}

button#save-template-1 {
  background-color: darkgreen;
}

button#run-template-1 {
  background-color: orange;
}

.k-value-selector  {
  color: black;
}

.popover button {
  background-color: #588274;
  border-width: 0;
}

</style>

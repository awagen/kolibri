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
            <option>search-evaluation</option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Template</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="templateSelectEvent($event)" class="form-select k-field" id="template-name-1">
            <option>Choose an option</option>
            <option v-for="templateName in templateNames">{{ templateName }}</option>
          </select>
        </div>
      </div>

      <!-- Text area for template edit -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-edit-1">Template Content</label>
        </div>
        <div class="col-9 col-sm-12">
          <textarea class="form-input k-area" id="template-edit-1" placeholder="Template Content" rows="3">
            {{selectedTemplateContent}}
          </textarea>
        </div>
      </div>
    </form>
    <!-- Other half is the status display for stuff already added -->
    <div class="col-6 column k-json-panel">
      <h3 class="title">
        JSON
      </h3>
      <pre v-html="created_json_string_state"/>
    </div>
  </div>
</template>

<script>
import {onMounted, ref} from "vue";
import {objectToJsonStringAndSyntaxHighlight} from "../utils/formatFunctions";
import axios from "axios";

export default {

  props: [],
  setup(props) {
    const created_state = ref({})
    const created_json_string_state = ref("")
    const templateNames = ref([])
    const getTemplatesURL = "http://localhost:8000/templates/jobs/overview?type=search-evaluation"
    const getTemplateContentURL = "http://localhost:8000/templates/jobs?type=search-evaluation"
    const selectedTemplateType = ref("")
    const selectedTemplateName = ref("")
    const selectedTemplateContent = ref("")

    const connection_button_expanded = ref(false)

    function jobTypeSelectEvent(event) {
      selectedTemplateType.value = event.target.value
      console.info(selectedTemplateType.value);
      retrieveTemplatesForType(selectedTemplateType.value)
    }

    function templateSelectEvent(event) {
      selectedTemplateName.value = event.target.value
      console.info(selectedTemplateName.value);
      retrieveTemplateContent(selectedTemplateType.value, selectedTemplateName.value)
    }

    function toggle_connection_add_button() {
      connection_button_expanded.value = !connection_button_expanded.value
    }

    function retrieveTemplatesForType(typeName) {
      return axios
          .get(getTemplatesURL)
          .then(response => {
            templateNames.value = response.data
          }).catch(_ => {
            templateNames.value = []
          })
    }

    function retrieveTemplateContent(typeName, templateName) {
      return axios
          .get(getTemplateContentURL + "?type=" + typeName + "&name=" + templateName)
          .then(response => {
            selectedTemplateContent.value = response.data
          }).catch(_ => {
            selectedTemplateContent.value = "";
          })
    }

    onMounted(() => {
    })

    return {
      created_state,
      created_json_string_state,
      connection_button_expanded,
      toggle_connection_add_button,
      templateNames,
      jobTypeSelectEvent,
      selectedTemplateType,
      selectedTemplateContent
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

</style>

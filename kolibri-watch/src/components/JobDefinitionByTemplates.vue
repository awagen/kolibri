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
            <option v-for="templateType in this.$store.state.templateState.templateTypes">{{ templateType }}</option>
          </select>
        </div>
        <div class="k-form-separator"></div>
        <!-- select the needed template based on above selection -->
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-name-1">Select Template</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="templateSelectEvent($event)" class="form-select k-field k-value-selector"
                  id="template-name-1">
            <option>Choose an option</option>
            <option v-for="templateName in this.$store.state.templateState.templateNames">{{ templateName }}</option>
          </select>
        </div>
      </div>

      <!-- Selector for field names in json which will open an edit window for that specific field with apply option -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-field-1">Select Template Field</label>
        </div>
        <div class="col-9 col-sm-12">
          <select @change="jobTemplateFieldEditSelectEvent($event)" class="form-select k-value-selector"
                  id="template-field-1">
            <option>Choose an option (or edit freely)</option>
            <option v-for="fieldName in Object.keys(this.$store.state.templateState.selectedTemplateContent)">{{ fieldName }}</option>
          </select>
        </div>
      </div>

      <!-- Text area for template edit -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="template-edit-1">Replace Template Content</label>
          <!-- if available, display some field info here -->
          <div v-if="this.$store.state.templateState.fieldInfoAvailable" class="popover popover-right">
            <button class="btn btn-action s-circle"><i class="icon icon-message"></i></button>
            <div class="popover-container">
              <div class="card">
                <div class="card-header">
                  <b>Field: {{ this.$store.state.templateState.selectedTemplateField }}</b>
                </div>
                <div class="card-body">
                  {{ this.$store.state.templateState.selectedTemplateFieldInfo }}
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
          <button type='button' @click="applyChanges()" class="k-form k-half k-right-padded btn btn-action"
                  id="apply-changes-1">
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

      <!-- Input controls -->
      <JobTemplateControls page-id="freeEdit"/>

      <div class="form-separator"></div>
      <pre id="template-content-display-1" v-html="this.$store.state.templateState.selectedTemplateJsonString"/>
    </form>
  </div>
</template>

<script>
import {onMounted} from "vue";
import {baseJsonFormatting} from "../utils/formatFunctions";
import {
  executeJob,
  saveTemplate
} from "../utils/retrievalFunctions";
import JobTemplateControls from "@/components/partials/JobTemplateControls.vue";

export default {

  props: [],
  components: {JobTemplateControls},
  methods: {
    jobTypeSelectEvent(event) {
      this.$store.commit("updateSelectedTemplateType", event.target.value)
    },

    templateSelectEvent(event) {
      this.$store.commit("updateSelectedTemplate", event.target.value)
    },

    jobTemplateFieldEditSelectEvent(event) {
      this.$store.commit("updateTemplateFieldEditSelection", event.target.value)
      document.getElementById("template-edit-1").value = baseJsonFormatting(this.$store.state.templateState.selectedTemplateFieldPartial)
    },

    applyChanges() {
      let changes = document.getElementById("template-edit-1").value
      this.$store.commit("updateTemplateState", changes)
    }
  },
  setup(props) {
    onMounted(() => {
    })
    return {}
  }
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

pre#template-content-display-1 {

  margin-top: 2em;

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

.k-value-selector {
  color: black;
}

.popover button {
  background-color: #588274;
  border-width: 0;
}

</style>

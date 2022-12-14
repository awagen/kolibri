<template>

  <!-- make jobs selectable and then display below definition content dependent upon selection -->
  <div class="row-container columns">
    <form class="form-horizontal col-6 column">

      <div class="form-group">
        <div class="col-12 col-sm-12">
          <label class="form-label" for="job-name-1">Select Job Name</label>
        </div>
        <div class="col-12 col-sm-12">
          <select @change="jobNameSelectEvent($event)" class="form-select k-value-selector" id="job-name-1">
            <option>Choose an option</option>
            <option v-for="jobName in availableJobNames">{{ jobName }}</option>
          </select>
        </div>
      </div>

      <template v-if="selectedJobName !== undefined && selectedJobName !==''">
        <div class="k-form-separator"></div>

        <div class="col-12 col-sm-12">
          <label class="form-label" for="template-name-1">Select Template</label>
        </div>
        <div class="col-12 col-sm-12">
          <select @change="jobTemplateSelectEvent($event)" class="form-select k-value-selector" id="template-name-1">
            <option>Choose an option</option>
            <option
                v-for="templateName in [...['None'], ...this.$store.state.availableJobTemplateIdsForType(selectedJobName)]">
              {{ templateName }}
            </option>
          </select>
        </div>
      </template>


    </form>
  </div>


  <template v-if="selectedJobName !== undefined && selectedJobName !==''">
    <div class="row-container columns">

      <form class="form-horizontal col-8 column">
        <h3 class="k-title">
          Job: {{ selectedJobShortDescription }}
        </h3>
        <h3 class="k-title">
          EndPoint: {{ selectedJobEndpoint }}
        </h3>
        <h3 class="k-description">
          {{ selectedJobDescription }}
        </h3>

        <template v-if="currentJobNestedStruct !== undefined">
          <NestedFieldSeqStructDef
              :key="componentKeyValue"
              @value-changed="valueChanged"
              :conditional-fields="currentJobNestedStruct.conditionalFields"
              :fields="currentJobNestedStruct.fields"
              :is-root="true"
              :init-with-value="selectedJobTemplate"
          >
          </NestedFieldSeqStructDef>
        </template>

        <!-- Input controls -->
        <JobTemplateControls></JobTemplateControls>

      </form>

      <!-- json overview container -->
      <template v-if="currentJobNestedStructJson !== undefined">
        <form class="form-horizontal col-4 column">
          <h3 class="k-title">
            JSON
          </h3>

          <div class="k-json-container col-12 col-sm-12">
            <pre id="template-content-display-1" v-html="currentJobNestedStructJson"/>
          </div>

        </form>
      </template>
    </div>
  </template>
</template>

<script>

import NestedFieldSeqStructDef from "../components/partials/structs/NestedFieldSeqStructDef.vue";
import {useStore} from "vuex";
import {ref, watch, computed} from "vue";
import JobTemplateControls from "../components/partials/JobTemplateControls.vue";

export default {

  props: {
    fillWithValue: {
      type: Object,
      required: false
    }
  },
  components: {JobTemplateControls, NestedFieldSeqStructDef},

  data() {
    return {
      componentKeyValue: ""
    }
  },

  methods: {

    changeComponentKeyValue(value) {
      this.componentKeyValue = value
    },

    jobNameSelectEvent(event) {
      this.$store.commit("updateSelectedJobName", event.target.value)
    },

    jobTemplateSelectEvent(event) {
      this.$store.commit("updateSelectedJobTemplate", event.target.value)
    },

    valueChanged(attributes) {
      this.$store.commit("updateCurrentJobDefState", attributes)
    }

  },

  computed: {

    selectedJobName() {
      return this.$store.state.jobInputDefState.selectedJobName
    },

    selectedJobTemplateName() {
      return this.$store.state.jobInputDefState.selectedJobTemplate
    },

    selectedJobShortDescription() {
      return this.$store.state.jobInputDefState.jobNameToShortDescription[this.selectedJobName]
    },

    selectedJobEndpoint() {
      return this.$store.state.jobInputDefState.jobNameToEndpoint[this.selectedJobName]
    },

    selectedJobDescription() {
      return this.$store.state.jobInputDefState.jobNameToDescription[this.selectedJobName]
    },

    selectedJobTemplate() {
      let selectedJobNameValue = this.$store.state.jobInputDefState.selectedJobName
      let selectedJobTemplateNameValue = this.$store.state.jobInputDefState.selectedJobTemplate
      return this.$store.state.templateContentForTemplateTypeAndId(selectedJobNameValue, selectedJobTemplateNameValue)
    },

    availableJobNames() {
      return Object.keys(this.$store.state.jobInputDefState.jobNameToInputDef)
    },

    availableJobTemplateTypes() {
      return Object.keys(this.$store.state.templateState.templateTypeToTemplateIdToContentMapping)
    },

    currentJobNestedStruct() {
      return this.$store.state.jobInputDefState.jobNameToInputStates[this.$store.state.jobInputDefState.selectedJobName]
    },

    currentJobNestedStructJson() {
      return this.$store.state.jobInputDefState.jobNameToInputStatesJson[this.$store.state.jobInputDefState.selectedJobName]
    }

  },

  mounted() {

    watch(() => this.$store.state.jobInputDefState.selectedJobTemplate, (newValue, oldValue) => {
      this.changeComponentKeyValue(newValue)
    })

  },

  setup() {
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

.k-value-selector {
  color: black;
}

.k-title {
  font-size: x-large;
}

.k-description {
  text-align: left;
  font-size: 1em;
  margin-bottom: 2em;
}

</style>
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
            <option v-for="templateName in [...['None'], ...this.$store.state.availableJobTemplateIdsForType(selectedJobName)]">{{ templateName }}</option>
          </select>
        </div>
      </template>


    </form>
  </div>


  <template v-if="selectedJobName !== undefined && selectedJobName !==''">
    <div class="row-container columns">

      <form class="form-horizontal col-8 column">
        <h3 class="k-title">
          Job: {{selectedJobShortDescription}}
        </h3>
        <h3 class="k-title">
          EndPoint: {{selectedJobEndpoint}}
        </h3>
        <h3 class="k-description">
          {{selectedJobDescription}}
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
  methods: {
  },
  setup(props, context) {
    const store = useStore()
    let componentKeyValue = ref("")


    let selectedJobName = computed(() => {
      return store.state.jobInputDefState.selectedJobName
    })

    let selectedJobTemplateName = computed(() => {
      return store.state.jobInputDefState.selectedJobTemplate
    })

    let selectedJobShortDescription = computed(() => {
      return store.state.jobInputDefState.jobNameToShortDescription[selectedJobName]
    })

    let selectedJobEndpoint = computed(() => {
      return store.state.jobInputDefState.jobNameToEndpoint[selectedJobName]
    })

    let selectedJobDescription = computed(() => {
      return store.state.jobInputDefState.jobNameToDescription[selectedJobName]
    })

    let selectedJobTemplate = computed(() => {
      let selectedJobNameValue = store.state.jobInputDefState.selectedJobName
      let selectedJobTemplateNameValue = store.state.jobInputDefState.selectedJobTemplate
      return store.state.templateContentForTemplateTypeAndId(selectedJobNameValue, selectedJobTemplateNameValue)
    })

    let availableJobNames = computed(() => {
      return Object.keys(store.state.jobInputDefState.jobNameToInputDef)
    })

    let availableJobTemplateTypes = computed(() => {
      return Object.keys(store.state.templateState.templateTypeToTemplateIdToContentMapping)
    })

    let currentJobNestedStruct = computed(() => {
      return store.state.jobInputDefState.jobNameToInputStates[store.state.jobInputDefState.selectedJobName]
    })

    let currentJobNestedStructJson = computed(() => {
      return store.state.jobInputDefState.jobNameToInputStatesJson[store.state.jobInputDefState.selectedJobName]
    })

    function changeComponentKeyValue(value) {
      componentKeyValue.value = value
    }

    watch(() => store.state.jobInputDefState.selectedJobTemplate, (newValue, oldValue) => {
      changeComponentKeyValue(newValue)
    })

    function jobNameSelectEvent(event) {
      store.commit("updateSelectedJobName", event.target.value)
    }

    function jobTemplateSelectEvent(event) {
      store.commit("updateSelectedJobTemplate", event.target.value)
    }

    function valueChanged(attributes) {
      store.commit("updateCurrentJobDefState",
          attributes)
    }

    return {
      jobNameSelectEvent,
      jobTemplateSelectEvent,
      valueChanged,
      componentKeyValue,
      selectedJobName,
      selectedJobTemplateName,
      selectedJobShortDescription,
      selectedJobEndpoint,
      selectedJobDescription,
      selectedJobTemplate,
      availableJobNames,
      availableJobTemplateTypes,
      currentJobNestedStruct,
      currentJobNestedStructJson
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

.k-title {
  font-size: x-large;
}

.k-description {
  text-align: left;
  font-size: 1em;
  margin-bottom: 2em;
}

</style>
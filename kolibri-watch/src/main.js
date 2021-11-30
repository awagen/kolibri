import {createApp} from 'vue'
import {createStore} from 'vuex'
import App from './App.vue'
import './index.css'
import router from './router'
import {
    retrieveJobs,
    retrieveNodeStatus,
    retrieveServiceUpState, retrieveTemplateContentAndInfo,
    retrieveTemplatesForType, retrieveTemplateTypes
} from './utils/retrievalFunctions'

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';
import {kolibriStateRefreshInterval} from "./utils/globalConstants";
import {objectToJsonStringAndSyntaxHighlight, stringifyObj} from "./utils/formatFunctions";

// Create a new store instance.
const store = createStore({
    state() {
        return {
            serviceIsUp: false,
            statusRefreshIntervalInMs: kolibriStateRefreshInterval,
            runningNodes: [],
            runningJobs: [],
            jobHistory: [],
            // template editing state

            // the names of template types for which specific templates can be requested
            templateTypes: [],
            // the names of the available templates as retrieved via the templates url
            templateNames: [],
            // template field selected for edit
            selectedTemplateField: "",
            // boolean to indicate whether any field info is available for the selected field
            fieldInfoAvailable: false,
            // the actual current value for the selectedTemplateField
            selectedTemplateFieldValue: "",
            // the info/description (if available) for the selectedTemplateField
            selectedTemplateFieldInfo: "",
            // the partial selected for editing, e.g {selectedTemplateField: selectedTemplateFieldValue}
            selectedTemplateFieldPartial: {},
            // selected type of template
            selectedTemplateType: "",
            // selected name of template
            selectedTemplateName: "",
            // the retrieved template content
            selectedTemplateContent: {},
            // description per field for the retrieved content
            selectedTemplateContentInfo: {},
            // the stringyfied values of the retrieved json
            selectedTemplateJsonString: "",
            // the changes to be applied to the selected template
            changedTemplateParts: {}
        }
    },

    mutations: {
        updateServiceUpState(state) {
            retrieveServiceUpState().then(response => state.serviceIsUp = response)
        },

        updateNodeStatus(state) {
            retrieveNodeStatus().then(response => state.runningNodes = response)
        },

        updateRunningJobs(state) {
            return retrieveJobs(false, x => {
                state.runningJobs = x
            })
        },

        updateJobHistory(state) {
            return retrieveJobs(true, x => {
                state.jobHistory = x
            })
        },

        updateAvailableTemplateTypes(state) {
            retrieveTemplateTypes().then(response => state.templateTypes = response)
        },

        updateSelectedTemplateType(state, type) {
            state.selectedTemplateType = type
            retrieveTemplatesForType(type).then(response => {
                state.templateNames = response
            })
        },

        updateSelectedTemplate(state, templateName) {
            state.selectedTemplateName = templateName
            retrieveTemplateContentAndInfo(state.selectedTemplateType, templateName).then(response => {
                if (Object.keys(response).length === 0) {
                    state.selectedTemplateContent = {};
                    state.selectedTemplateContentInfo = {}
                    state.selectedTemplateJsonString = "";
                } else {
                    state.selectedTemplateContent = response["template"]
                    state.selectedTemplateContentInfo = response["info"]
                    state.selectedTemplateJsonString = objectToJsonStringAndSyntaxHighlight(state.selectedTemplateContent)
                }
            })
        },

        updateTemplateFieldEditSelection(state, templateField) {
            state.selectedTemplateFieldPartial = {}
            state.selectedTemplateField = templateField
            // load the current field value for the selected field and the info (if any is provided)
            state.selectedTemplateFieldValue = state.selectedTemplateContent[state.selectedTemplateField]
            state.selectedTemplateFieldInfo = state.selectedTemplateContentInfo[state.selectedTemplateField]
            state.fieldInfoAvailable = state.selectedTemplateFieldInfo != null
            state.selectedTemplateFieldPartial[state.selectedTemplateField] = state.selectedTemplateFieldValue
        },

        updateTemplateState(state, changes) {
            state.changedTemplateParts = JSON.parse(changes)
            state.selectedTemplateContent = Object.assign({}, JSON.parse(stringifyObj(state.selectedTemplateContent)), state.changedTemplateParts)
            state.selectedTemplateJsonString = objectToJsonStringAndSyntaxHighlight(state.selectedTemplateContent)
        }


    }
})

// initial service status check
store.commit("updateServiceUpState")
store.commit("updateNodeStatus")
store.commit("updateRunningJobs")
store.commit("updateJobHistory")
store.commit("updateAvailableTemplateTypes")
// regular scheduling
window.setInterval(() => {
    store.commit("updateServiceUpState")
    store.commit("updateNodeStatus")
    store.commit("updateRunningJobs")
    store.commit("updateJobHistory")
}, store.state.statusRefreshIntervalInMs)


const app = createApp(App);
app.use(router)
// https://next.vuex.vuejs.org/guide/
app.use(store)
app.mount('#app');

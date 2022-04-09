import {createApp} from 'vue'
import {createStore} from 'vuex'
import App from './App.vue'
import './index.css'
import router from './router'
import {
    retrieveJobs,
    retrieveNodeStatus,
    retrieveServiceUpState, retrieveTemplateContentAndInfo,
    retrieveTemplatesForType, retrieveTemplateTypes,
    retrieveDataFileInfoAll,
    retrieveExecutionIDs, retrieveSingleResultIDsForExecutionID,
    retrieveSingleResultById, retrieveSingleResultByIdFiltered,
    retrieveAnalysisTopFlop, retrieveAnalysisVariance
} from './utils/retrievalFunctions'

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';
import {kolibriStateRefreshInterval} from "./utils/globalConstants";
import {objectToJsonStringAndSyntaxHighlight, stringifyObj} from "./utils/formatFunctions";
import {filteredResultsReduced} from "./utils/dataFunctions";

// Create a new store instance.
const store = createStore({
    state() {
        return {
            serviceIsUp: false,
            statusRefreshIntervalInMs: kolibriStateRefreshInterval,
            runningNodes: [],
            runningJobs: [],
            jobHistory: [],

            // data files available
            standaloneFileDataByType: {},
            mappingFileDataByType: {},
            selectedStandaloneDataFileType: "",
            selectedMappingDataFileType: "",
            // the files added to the composer so far (standalone values or mappings)
            selectedData: [],
            // selectedDataMapping should contain one standalone data sample to provide keys and
            // arbitrary number of values mapped to a key or another mapped value before it in the sequence
            // and is the one selected for editing
            selectedDataMapping: {},

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
            changedTemplateParts: {},

            // result states
            availableResultExecutionIDs: [],
            currentlySelectedExecutionID: "",
            availableResultsForSelectedExecutionID: [],
            fullResultForExecutionIDAndResultID: {},
            filteredResultForExecutionIDAndResultID: {},
            reducedFilteredResultForExecutionIDAndResultID: {},
            // analysis states
            analysisTopFlop: {},
            analysisVariances: {}
        }
    },

    mutations: {
        updateAvailableResultExecutionIDs(state) {
            retrieveExecutionIDs().then(response => state.availableResultExecutionIDs = response)
        },

        updateAvailableResultsForExecutionID(state, executionId) {
            state.currentlySelectedExecutionID = executionId
            retrieveSingleResultIDsForExecutionID(executionId)
                .then(response => state.availableResultsForSelectedExecutionID = response.sort())
        },

        updateSingleResultState(state, {executionId, resultId}) {
            retrieveSingleResultById(executionId, resultId)
                .then(response => state.fullResultForExecutionIDAndResultID = response)
        },

        updateSingleResultStateFiltered(state, {executionId, resultId, metricName, topN, reversed}) {
            retrieveSingleResultByIdFiltered(executionId, resultId, metricName, topN, reversed)
                .then(response => {
                    state.filteredResultForExecutionIDAndResultID = response
                    state.reducedFilteredResultForExecutionIDAndResultID = filteredResultsReduced(response)
                })
        },

        updateAnalysisTopFlop(state, {
            executionId, currentParams, compareParams, metricName, queryParamName,
            n_best, n_worst
        }) {
            retrieveAnalysisTopFlop(executionId, currentParams, compareParams, metricName, queryParamName, n_best, n_worst)
                .then(response => state.analysisTopFlop = response)
        },

        updateAnalysisVariance(state, {executionId, metricName, queryParamName}) {
            retrieveAnalysisVariance(executionId, metricName, queryParamName)
                .then(response => state.analysisVariances = response)
        },

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

        updateAvailableDataFiles(state, numReturnSamples) {
            retrieveDataFileInfoAll(numReturnSamples).then(response => {
                console.info("retrieved available data response: ")
                console.log(response)
                state.standaloneFileDataByType = {}
                state.mappingFileDataByType = {}
                Object.keys(response).forEach(group => {
                    state.standaloneFileDataByType[group] = response[group].filter(data => {
                        return data["isMapping"] === false
                    })
                    state.mappingFileDataByType[group] = response[group].filter(data => {
                        return data["isMapping"] === true
                    })
                })
                if (Object.keys(state.standaloneFileDataByType).length > 0 && state.selectedStandaloneDataFileType === "") {
                    state.selectedStandaloneDataFileType = Object.keys(state.standaloneFileDataByType)[0]
                }
                if (Object.keys(state.mappingFileDataByType).length > 0 && state.selectedMappingDataFileType === "") {
                    state.selectedMappingDataFileType = Object.keys(state.mappingFileDataByType)[0]
                }
            })
        },

        updateSelectedStandaloneDataFileType(state, fileType) {
            state.selectedStandaloneDataFileType = fileType
        },

        updateSelectedMappingDataFileType(state, fileType) {
            state.selectedMappingDataFileType = fileType
        },

        /**
         * Add some non-mapping data sample to the list of parameters
         *
         * @param state
         * @param fileObj
         */
        addSelectedDataFile(state, fileObj) {
            if (fileObj.isMapping) {
                console.info("trying to add mapping as standalone data. Will be ignored.")
                return
            }
            let newElement = {
                "type": "standalone",
                "data": fileObj
            }
            console.info("adding standalone data file:")
            console.log(newElement)
            state.selectedData.push(newElement)

        },

        /**
         * Given either standalone data or mapped value, add this to a mapping.
         * In case a standalone value is added, a new mapping is added with the value as keyValues.
         * In case a mapping is added, this will be appended to the mappings of the selectedDataMapping.
         * In case no mapping has yet been initialized with some key value, adding a mapping will
         * not work, since it requires some selectedDataMapping which is set to the latest mapping
         * created by adding a key value.
         * @param state
         * @param fileObj
         */
        addSelectedDataToMapping(state, fileObj) {
            if (!fileObj.isMapping) {
                // create new mapping and add both to selectedData and to selectedDataMapping
                let newMapping = {
                    "type": "mapping",
                    "data": {
                        "keyValues": fileObj,
                        "mappedValues": [],
                        "mappedToIndices": []
                    }
                }
                console.info("adding standalone value as mapping key:")
                console.log(newMapping)
                state.selectedDataMapping = newMapping
                state.selectedData.push(newMapping)
            } else {
                if (state.selectedDataMapping === null || Object.keys(state.selectedDataMapping).length === 0) {
                    console.info("can not add mapped values since no selected data mapping exists." +
                        "Create one first by adding standalone data as key values.")
                    return
                }
                console.info("adding mapped value to existing mapping:")
                console.log(fileObj)
                state.selectedDataMapping.data.mappedValues.push(fileObj)
                // NOTE: the index related to mapped values itself is one less, but what we record as index
                // here is taking keys on index 0 into account
                let newMappedValueIndex = state.selectedDataMapping.data.mappedValues.length
                // NOTE: the indices are in the order key-provider, mapping-provider index
                // and strictly holds key-provider-index < mapping-provider-index
                // per default is mapped to the keys, but can be adjusted to relate to any of
                // the indices referring to a mapping that comes before in the sequence
                state.selectedDataMapping.data.mappedToIndices.push([0, newMappedValueIndex])
            }
        },

        /**
         * Removes standalone data from the selected data
         * @param state
         * @param fileObj
         */
        removeSelectedDataFile(state, fileObj) {
            let toBeRemoved = state.selectedData.filter(element => {
                return element.type === "standalone" && element.data["fileType"] === fileObj["fileType"] &&
                    element.data["fileName"] === fileObj["fileName"];
            });
            if (toBeRemoved.length > 0) {
                state.selectedData.splice(state.selectedData.indexOf(toBeRemoved[toBeRemoved.length - 1]), 1)
            }
        },

        /**
         * Removes a complete mapping from selected data
         * @param state
         * @param mappingObj
         */
        removeSelectedMapping(state, mappingObj) {
            console.info("remove selected state from mapping obj")
            console.log(state)
            console.log(mappingObj)
            let removeIndex = state.selectedData.map(x => x.data).indexOf(mappingObj)
            console.info("found index of mapping obj in selectedData" + removeIndex)
            console.info("selected data")
            console.log(state.selectedData)
            if (removeIndex >= 0) {
                state.selectedData.splice(removeIndex, 1)
            }
            if (state.selectedDataMapping.data === mappingObj) {
                state.selectedDataMapping = {}
            }
        },

        /**
         * Deletes a single mapped value from a given mapping
         * @param state
         * @param fileObj
         * @param mappingObj
         */
        removeMappingFromMappedValues(state, {fileObj, mappingObj}) {
            let removeFromMappingIndex = state.selectedData.map(x => x.data).indexOf(mappingObj)
            if (removeFromMappingIndex < 0) {
                console.info("mapping obj not found, can not delete data from it")
                return
            }
            let removeFrom = state.selectedData[removeFromMappingIndex].data
            let removeIndex = removeFrom.mappedValues.indexOf(fileObj)
            if (removeIndex >= 0) {
                removeFrom.mappedValues.splice(removeIndex, 1)
                removeFrom.mappedToIndices.splice(removeIndex, 1)
            }
            else {
                console.info("could not find fileObj in mappingObj. Ignoring request for deletion")
            }
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
    },
    computed: {}
})

// initial service status check
store.commit("updateServiceUpState")
store.commit("updateNodeStatus")
store.commit("updateRunningJobs")
store.commit("updateJobHistory")
store.commit("updateAvailableTemplateTypes")
store.commit("updateAvailableDataFiles", 5)
// initial loading of executionIds for which results are available
store.commit("updateAvailableResultExecutionIDs")

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

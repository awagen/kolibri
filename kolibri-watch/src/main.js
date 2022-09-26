import {createApp} from 'vue'
import {createStore} from 'vuex'
import App from './App.vue'
import './index.css'
import router from './router'
import {
    retrieveJobs,
    retrieveNodeStatus,
    retrieveServiceUpState,
    retrieveTemplateContentAndInfo,
    retrieveTemplatesForType,
    retrieveTemplateTypes,
    retrieveDataFileInfoAll,
    retrieveExecutionIDs,
    retrieveSingleResultIDsForExecutionID,
    retrieveSingleResultById,
    retrieveSingleResultByIdFiltered,
    retrieveAnalysisTopFlop,
    retrieveAnalysisVariance,
    retrieveRequestSamplesForData,
    retrieveAllAvailableIRMetrics,
    changeReducedToFullMetricsJsonList,
    retrieveJobInformation
} from './utils/retrievalFunctions'

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';
import {kolibriStateRefreshInterval} from "./utils/globalConstants";
import {objectToJsonStringAndSyntaxHighlight, stringifyObj} from "./utils/formatFunctions";
import {filteredResultsReduced, selectedDataToParameterValuesJson} from "./utils/dataFunctions";
import {idForMetric} from "./utils/metricFunctions";
import {objToInputDef} from "@/utils/inputDefConversions";

// Create a new store instance.
const store = createStore({
    state() {
        return {
            serviceState: {
                serviceIsUp: false,
                statusRefreshIntervalInMs: kolibriStateRefreshInterval,
                runningNodes: [],
                runningJobs: [],
                jobHistory: []
            },

            // data files available
            dataState: {
                standaloneFileDataByType: {},
                mappingFileDataByType: {},
                selectedStandaloneDataFileType: "",
                selectedMappingDataFileType: "",
                // the files added to the composer so far (standalone values or mappings)
                selectedData: [],
                selectedDataRequestSamples: [],
                // the stringified json array representing the selected data values
                // this should directly reflect the json representation of a list of
                // ParameterValue instances (standalone or mapping), that can be directly
                // consumed by the backend (ParameterValuesJsonProtocol)
                selectedDataJsonString: "",
                // selectedDataMapping should contain one standalone data sample to provide keys and
                // arbitrary number of values mapped to a key or another mapped value before it in the sequence
                // and is the one selected for editing
                selectedDataMapping: {}
            },

            metricState: {
                // list of available IR metrics
                availableIRMetrics: [],
                // list of selected IR metrics
                selectedIRMetrics: [],
                // conversion of selected IR metrics to respective json definition needed by the job config
                selectedIRMetricsJson: [],
                selectedIRMetricsFormattedJsonString: ""
            },

            // state representing json selectors to retrieve fields from responses
            // that can be utilized to calculate metrics
            parsingState: {
                selectedParsingSelectors: [],
                selectedParsingSelectorsFormattedJsonString: ""
            },

            templateState: {
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
            },

            resultState: {
                // result states
                availableResultExecutionIDs: [],
                currentlySelectedExecutionID: "",
                availableResultsForSelectedExecutionID: [],
                fullResultForExecutionIDAndResultID: {},
                filteredResultForExecutionIDAndResultID: {},
                reducedFilteredResultForExecutionIDAndResultID: {}
            },

            analysisState: {
                // analysis states
                analysisTopFlop: {},
                analysisVariances: {}
            },

            jobInputDefState: {
                // new structure to load an arbitrary number of endpoints and selectively configure
                jobNameToInputDef: {},
                jobNameToEndpoint: {},
                jobNameToDescription: {},
                // the name of the currently selected template
                selectedJobName: "",
                // the mapping of job name to current states of the inputs
                jobNameToInputStatesObj: {},
                jobNameToInputStates: {},
                jobNameToInputStatesJson: {},


                // current settings that just take details for the search evaluation use case
                searchEvalInputDef: {},
                searchEvalEndPoint: "",
                searchEvalJobDefState: {},
                searchEvalJobDefJsonString: ""
            }
        }
    },

    mutations: {
        retrieveJobDefinitions(state) {
            retrieveJobInformation().then(response => {
                console.info("job response: ")
                console.log(response)
                let requiredJobDefObj = response["payloadDef"]
                console.info("search evaluation job struct def:")
                console.log(requiredJobDefObj)
                let name = response["name"]
                let description = response["description"]
                let endpoint = response["endpoint"]
                let inputDef = objToInputDef(
                    requiredJobDefObj,
                    "root",
                    0
                )
                // filling in the new structure, e.g. mappings based on job names
                state.jobInputDefState.jobNameToInputDef[name] = inputDef
                state.jobInputDefState.jobNameToEndpoint[name] = endpoint
                state.jobInputDefState.jobNameToDescription[name] = description
                // filling in the edit state
                state.jobInputDefState.jobNameToInputStates[name] = inputDef.copy("root")

                // filling in current structure
                state.jobInputDefState.searchEvalInputDef = inputDef
                state.jobInputDefState.searchEvalEndPoint = endpoint
            })
        },

        updateSelectedJobName(state, jobName) {
            state.jobInputDefState.selectedJobName = jobName
        },

        updateCurrentJobDefState(state, {jobDefStateObj, jobDefState}) {
            // TODO: adjust to the fact that we have a) a json structure which is currently passed and the current state
            // of the structural def with all the default values set to keep the selections.
            // make sure to update both
            state.jobInputDefState.jobNameToInputStatesObj[state.jobInputDefState.selectedJobName] = jobDefStateObj
            state.jobInputDefState.jobNameToInputStatesJson[state.jobInputDefState.selectedJobName] = objectToJsonStringAndSyntaxHighlight(jobDefStateObj)
            state.jobInputDefState.jobNameToInputStates[state.jobInputDefState.selectedJobName] = jobDefState
        },

        recalculateSelectedDataJsonString(state) {
            let jsonObj = selectedDataToParameterValuesJson(state.dataState.selectedData)
            state.dataState.selectedDataJsonString = objectToJsonStringAndSyntaxHighlight(jsonObj)
        },

        retrieveRequestSamplesForSelectedData(state) {
          state.dataState.selectedDataRequestSamples = []
          let dataObj = selectedDataToParameterValuesJson(state.dataState.selectedData)
            retrieveRequestSamplesForData(dataObj, 10).then(response => state.dataState.selectedDataRequestSamples = response)
        },

        updateAvailableResultExecutionIDs(state) {
            retrieveExecutionIDs().then(response => state.resultState.availableResultExecutionIDs = response)
        },

        updateAvailableResultsForExecutionID(state, executionId) {
            state.resultState.currentlySelectedExecutionID = executionId
            retrieveSingleResultIDsForExecutionID(executionId)
                .then(response => state.resultState.availableResultsForSelectedExecutionID = response.sort())
        },

        updateSingleResultState(state, {executionId, resultId}) {
            retrieveSingleResultById(executionId, resultId)
                .then(response => state.resultState.fullResultForExecutionIDAndResultID = response)
        },

        updateSingleResultStateFiltered(state, {executionId, resultId, metricName, topN, reversed}) {
            retrieveSingleResultByIdFiltered(executionId, resultId, metricName, topN, reversed)
                .then(response => {
                    state.resultState.filteredResultForExecutionIDAndResultID = response
                    state.resultState.reducedFilteredResultForExecutionIDAndResultID = filteredResultsReduced(response)
                })
        },

        updateAnalysisTopFlop(state, {
            executionId, currentParams, compareParams, metricName, queryParamName,
            n_best, n_worst
        }) {
            retrieveAnalysisTopFlop(executionId, currentParams, compareParams, metricName, queryParamName, n_best, n_worst)
                .then(response => state.analysisState.analysisTopFlop = response)
        },

        updateAnalysisVariance(state, {executionId, metricName, queryParamName}) {
            retrieveAnalysisVariance(executionId, metricName, queryParamName)
                .then(response => state.analysisState.analysisVariances = response)
        },

        updateServiceUpState(state) {
            retrieveServiceUpState().then(response => state.serviceState.serviceIsUp = response)
        },

        updateNodeStatus(state) {
            retrieveNodeStatus().then(response => state.serviceState.runningNodes = response)
        },

        updateRunningJobs(state) {
            return retrieveJobs(false, x => {
                state.serviceState.runningJobs = x
            })
        },

        updateJobHistory(state) {
            return retrieveJobs(true, x => {
                state.serviceState.jobHistory = x
            })
        },

        updateAvailableDataFiles(state, numReturnSamples) {
            retrieveDataFileInfoAll(numReturnSamples).then(response => {
                console.info("retrieved available data response: ")
                console.log(response)
                state.dataState.standaloneFileDataByType = {}
                state.dataState.mappingFileDataByType = {}
                Object.keys(response).forEach(group => {
                    state.dataState.standaloneFileDataByType[group] = response[group].filter(data => {
                        return data["isMapping"] === false
                    })
                    state.dataState.mappingFileDataByType[group] = response[group].filter(data => {
                        return data["isMapping"] === true
                    })
                })
                if (Object.keys(state.dataState.standaloneFileDataByType).length > 0 && state.dataState.selectedStandaloneDataFileType === "") {
                    state.dataState.selectedStandaloneDataFileType = Object.keys(state.dataState.standaloneFileDataByType)[0]
                }
                if (Object.keys(state.dataState.mappingFileDataByType).length > 0 && state.dataState.selectedMappingDataFileType === "") {
                    state.dataState.selectedMappingDataFileType = Object.keys(state.dataState.mappingFileDataByType)[0]
                }
            })
        },

        updateSelectedStandaloneDataFileType(state, fileType) {
            state.dataState.selectedStandaloneDataFileType = fileType
        },

        updateSelectedMappingDataFileType(state, fileType) {
            state.dataState.selectedMappingDataFileType = fileType
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
            state.dataState.selectedData.push(newElement)
            this.commit("recalculateSelectedDataJsonString")
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
                        "mappedValues": []
                    }
                }
                console.info("adding standalone value as mapping key:")
                console.log(newMapping)
                state.dataState.selectedDataMapping = newMapping
                state.dataState.selectedData.push(newMapping)
            } else {
                if (state.dataState.selectedDataMapping === null || Object.keys(state.dataState.selectedDataMapping).length === 0) {
                    console.info("can not add mapped values since no selected data mapping exists." +
                        "Create one first by adding standalone data as key values.")
                    return
                }
                console.info("adding mapped value to existing mapping:")
                console.log(fileObj)
                state.dataState.selectedDataMapping.data.mappedValues.push(fileObj)
                fileObj["mappedToIndex"] = 0
            }
            this.commit("recalculateSelectedDataJsonString")
        },

        /**
         * Removes standalone data from the selected data
         * @param state
         * @param fileObj
         */
        removeSelectedDataFile(state, fileObj) {
            let toBeRemoved = state.dataState.selectedData.filter(element => {
                return element.type === "standalone" && element.data["fileType"] === fileObj["fileType"] &&
                    element.data["fileName"] === fileObj["fileName"];
            });
            if (toBeRemoved.length > 0) {
                state.dataState.selectedData.splice(state.dataState.selectedData.indexOf(toBeRemoved[toBeRemoved.length - 1]), 1)
            }
            this.commit("recalculateSelectedDataJsonString")
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
            let removeIndex = state.dataState.selectedData.map(x => x.data).indexOf(mappingObj)
            console.info("found index of mapping obj in selectedData" + removeIndex)
            console.info("selected data")
            console.log(state.dataState.selectedData)
            if (removeIndex >= 0) {
                state.dataState.selectedData.splice(removeIndex, 1)
            }
            if (state.dataState.selectedDataMapping.data === mappingObj) {
                state.dataState.selectedDataMapping = {}
            }
            this.commit("recalculateSelectedDataJsonString")
        },

        /**
         * Deletes a single mapped value from a given mapping
         * @param state
         * @param fileObj
         * @param mappingObj
         */
        removeMappingFromMappedValues(state, {fileObj, mappingObj}) {
            let removeFromMappingIndex = state.dataState.selectedData.map(x => x.data).indexOf(mappingObj)
            if (removeFromMappingIndex < 0) {
                console.info("mapping obj not found, can not delete data from it")
                return
            }
            let removeFrom = state.dataState.selectedData[removeFromMappingIndex].data
            let removeIndex = removeFrom.mappedValues.indexOf(fileObj)
            if (removeIndex >= 0) {
                removeFrom.mappedValues.splice(removeIndex, 1)
            }
            else {
                console.info("could not find fileObj in mappingObj. Ignoring request for deletion")
            }
            this.commit("recalculateSelectedDataJsonString")
        },

        updateAvailableTemplateTypes(state) {
            retrieveTemplateTypes().then(response => state.templateState.templateTypes = response)
        },

        updateSelectedTemplateType(state, type) {
            state.templateState.selectedTemplateType = type
            retrieveTemplatesForType(type).then(response => {
                state.templateState.templateNames = response
            })
        },

        updateSelectedTemplate(state, templateName) {
            state.templateState.selectedTemplateName = templateName
            retrieveTemplateContentAndInfo(state.templateState.selectedTemplateType, templateName).then(response => {
                if (Object.keys(response).length === 0) {
                    state.templateState.selectedTemplateContent = {};
                    state.templateState.selectedTemplateContentInfo = {}
                    state.templateState.selectedTemplateJsonString = "";
                } else {
                    state.templateState.selectedTemplateContent = response["template"]
                    state.templateState.selectedTemplateContentInfo = response["info"]
                    state.templateState.selectedTemplateJsonString = objectToJsonStringAndSyntaxHighlight(state.templateState.selectedTemplateContent)
                }
            })
        },

        updateTemplateFieldEditSelection(state, templateField) {
            state.templateState.selectedTemplateFieldPartial = {}
            state.templateState.selectedTemplateField = templateField
            // load the current field value for the selected field and the info (if any is provided)
            state.templateState.selectedTemplateFieldValue = state.templateState.selectedTemplateContent[state.templateState.selectedTemplateField]
            state.templateState.selectedTemplateFieldInfo = state.templateState.selectedTemplateContentInfo[state.templateState.selectedTemplateField]
            state.templateState.fieldInfoAvailable = state.templateState.selectedTemplateFieldInfo != null
            state.templateState.selectedTemplateFieldPartial[state.templateState.selectedTemplateField] = state.templateState.selectedTemplateFieldValue
        },

        updateTemplateState(state, changes) {
            state.templateState.changedTemplateParts = JSON.parse(changes)
            state.templateState.selectedTemplateContent = Object.assign({}, JSON.parse(stringifyObj(state.templateState.selectedTemplateContent)), state.templateState.changedTemplateParts)
            state.templateState.selectedTemplateJsonString = objectToJsonStringAndSyntaxHighlight(state.templateState.selectedTemplateContent)
        },

        updateAvailableIRMetrics(state){
            retrieveAllAvailableIRMetrics().then(response => {
                state.metricState.availableIRMetrics = response
            })
        },

        updateSelectedIRMetricsJson(state){
            // remove the id generated for identification purposes before sending to json transformation
            // endpoint
            changeReducedToFullMetricsJsonList(state.metricState.selectedIRMetrics.map(metric => {
                let newObj = Object.assign({}, metric)
                delete newObj["kId"]
                return newObj
            }))
                .then(response => {
                    state.metricState.selectedIRMetricsJson = response
                    state.metricState.selectedIRMetricsFormattedJsonString = objectToJsonStringAndSyntaxHighlight(response)
                })
        },

        addIRMetricToSelected(state, metricType) {
            console.info("adding metric to selected: " + metricType)
            let existingMetricIds = state.metricState.selectedIRMetrics.map(x => x.kId)
            let addCandidates = state.metricState.availableIRMetrics
                .filter(metric => metric.type === metricType)
                .map(metric => Object.assign({}, metric))
                // add id to check against on further adds
                .map(metric => {
                    metric["kId"] = idForMetric(metric)
                    return metric
                })
                .filter(metric => existingMetricIds.indexOf(metric.kId) < 0)
            addCandidates.forEach(metric => state.metricState.selectedIRMetrics.push(metric))
            this.commit("updateSelectedIRMetricsJson")
        },

        removeMetricIdFromSelected(state, metricId) {
            state.metricState.selectedIRMetrics = state.metricState.selectedIRMetrics
                .filter(metric => !(metric.kId === metricId))
            this.commit("updateSelectedIRMetricsJson")
        },

        updateSelectedParsingSelectorsJson(state){
            state.parsingState.selectedParsingSelectorsFormattedJsonString = objectToJsonStringAndSyntaxHighlight(
                state.parsingState.selectedParsingSelectors
            )
        },

        removeParsingSelectorFromSelected(state, name){
            state.parsingState.selectedParsingSelectors = state.parsingState.selectedParsingSelectors
                .filter(selector => !(selector.name === name))
            this.commit("updateSelectedParsingSelectorsJson")
        },

        addParsingSelector(state, {fieldName, selector, fieldType}) {
            let selectorJson = {"name": fieldName, "selector": selector, "castType": fieldType}
            let existingSelectors = state.parsingState.selectedParsingSelectors.map(sel => sel.name)
            if (existingSelectors.indexOf(selectorJson.name) < 0) {
                state.parsingState.selectedParsingSelectors.push(selectorJson)
                // update the json representation
                this.commit("updateSelectedParsingSelectorsJson")
            }
        }

    },
    actions: {}
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
// load list of available ir metrics
store.commit("updateAvailableIRMetrics")
// load job definition for search evaluation
store.commit("retrieveJobDefinitions")

// regular scheduling
window.setInterval(() => {
    store.commit("updateServiceUpState")
    store.commit("updateNodeStatus")
    store.commit("updateRunningJobs")
    store.commit("updateJobHistory")
}, store.state.serviceState.statusRefreshIntervalInMs)


const app = createApp(App);
app.use(router)
// https://next.vuex.vuejs.org/guide/
app.use(store)
app.mount('#app');

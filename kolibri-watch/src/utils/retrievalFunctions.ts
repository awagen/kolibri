import axios from "axios";
import {
    jobHistoryUrl, jobStateUrl, appIsUpUrl, nodeStateUrl, templateOverviewForTypeUrl,
    templateTypeUrl, templateSaveUrl, templateExecuteUrl,
    templateContentUrl, dataFileInfoAllUrl,
    resultExecutionIdsOverviewUrl, resultExecutionIdsSingleResultsOverviewUrl,
    resultExecutionIdGetDataByIdUrl, resultExecutionIdGetFilteredDataByIdUrl,
    resultAnalysisTopFlowUrl, resultAnalysisVarianceUrl, parameterValuesSampleRequestUrl,
    irMetricsAllUrl, irMetricsReducedToFullJsonListUrl, kolibriSearchEvalJobDefinitionUrl,
    templateAllTypeToInfoMapUrl
} from '../utils/globalConstants'


/**
 * Retrieve list of execution / experiment ids for which results are available
 */
function retrieveExecutionIDs() {
    const url = resultExecutionIdsOverviewUrl
    return axios
        .get(url)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

/**
 * For a given execution / experiment ID, provide list of
 * single partial / aggregated results
 * @param executionId
 */
function retrieveSingleResultIDsForExecutionID(executionId) {
    const url = resultExecutionIdsSingleResultsOverviewUrl.replace("#EXECUTION_ID", executionId)
    return axios
        .get(url)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

/**
 * retrieve a single result for an executionId (execitionId refers to a single experiment / execution)
 * @param executionId - the id of the execution / experiment
 * @param resultId - id of the result, usually will correspond to result file name
 */
function retrieveSingleResultById(executionId, resultId) {
    const url = resultExecutionIdGetDataByIdUrl.replace("#EXECUTION_ID", executionId) + "?id=" + resultId
    return axios
        .get(url)
        .then(response => {
            let result = response.data
            let returnValue = {}
            // list of parameter names (parameters valied)
            returnValue["paramNames"] = result["paramNames"]
            // list of metric names (single metrics calculated)
            returnValue["metricNames"] = result["metricNames"]
            // list of all column names, includes parameter names and
            // prefixed metricNames (e.g metric name prefixed with "value-[metricName]")
            returnValue["columnNames"] = result["columnNames"]
            // actual data lines. This is list of lists, where each top-level list represents
            // a row and the contained list the distinct columns corresponding to columnNames
            returnValue["dataLinesAsColumns"] = result["dataLinesAsColumns"]
            return returnValue
        }).catch(_ => {
            return {}
        })
}

/**
 * Similar to retrieveSingleResultById, yet allows sorting by passed metric name
 * and only return topN results. If topN < 0, all results will be retrieved.
 * If reversed is set to true, will reverse sorting, e.g with metric value asc.
 * @param executionId
 * @param resultId
 * @param metricName
 * @param topN
 * @param reversed
 */
function retrieveSingleResultByIdFiltered(executionId, resultId, metricName, topN, reversed) {
    const url = resultExecutionIdGetFilteredDataByIdUrl
        .replace("#EXECUTION_ID", executionId)
        + "?id=" + resultId
        + "&sortByMetric=" +  metricName
        + "&topN=" + topN
        + "&reversed=" + reversed
    return axios
        .get(url)
        .then(response => {
            let result = response.data
            let returnValue = {}
            // list of parameter names (parameters valied)
            returnValue["paramNames"] = result["paramNames"]
            // list of metric names (single metrics calculated)
            returnValue["metricNames"] = result["metricNames"]
            // list of all column names, includes parameter names and
            // prefixed metricNames (e.g metric name prefixed with "value-[metricName]")
            returnValue["columnNames"] = result["columnNames"]
            // actual data lines. This is list of lists, where each top-level list represents
            // a row and the contained list the distinct columns corresponding to columnNames
            returnValue["dataLinesAsColumns"] = result["dataLinesAsColumns"]
            return returnValue
        }).catch(_ => {
            return {}
        })
}

/**
 * Retrieve for a given execution / experiment the top winning / loosing queries
 * for given parameter setting compared with a current parameter setting.
 * The criterion to determine winners / loosers is the change in value for
 * the metric given by metricName (e.g NDCG_10).
 * Returns at most n_best, n_worst respectively for winner / looser queries.
 * @param executionId
 * @param currentParams
 * @param compareParams
 * @param metricName
 * @param queryParamName
 * @param n_best
 * @param n_worst
 */
function retrieveAnalysisTopFlop(executionId,
                                 currentParams,
                                 compareParams,
                                 metricName,
                                 queryParamName,
                                 n_best,
                                 n_worst) {
    const url = resultAnalysisTopFlowUrl
    const data = {
        "type": "ANALYZE_BEST_WORST_REGEX",
        "directory": executionId,
        "regex": ".*[(]q=.+[)].*",
        "currentParams": currentParams,
        "compareParams": compareParams,
        "metricName": metricName,
        "queryParamName": queryParamName,
        "n_best": n_best,
        "n_worst": n_worst
    }
    console.log(data)
    const config = {
        headers: {
            "Content-Type" : "application/json"
        }
    }
    return axios
        .post(url, data,  config)
        .then(response => {
            console.info("retrieved top flop results")
            console.log(response)
            return response.data
        }).catch(_ => {
            {}
        })
}

/**
 * Retrieve for a given execution / experiment the variance regarding the
 * metric given by metricName (e.g NDCG_10) over all queries.
 * Result is sorted desc by variance value.
 * Serves to understand if, given the distinct parameter settings, the results
 * for a given query vary or remain relatively stale (e.g due to manual editing,
 * which prevents passed parameters to have effect)
 * @param executionId
 * @param metricName
 * @param queryParamName
 */
function retrieveAnalysisVariance(executionId,
                                 metricName,
                                 queryParamName) {
    const url = resultAnalysisVarianceUrl
    const data = {
        "type": "ANALYZE_QUERY_METRIC_VARIANCE",
        "directory": executionId,
        "regex": ".*[(]q=.+[)].*",
        "metricName": metricName,
        "queryParamName": queryParamName
    }
    const config = {
        headers: {
            "Content-Type" : "application/json"
        }
    }
    return axios
        .post(url, data, config)
        .then(response => {
            console.info("retrieved variances results")
            console.log(response)
            return response.data
        }).catch(_ => {
            {}
        })
}

function retrieveJobs(historical, updateFunc) {
    const url = historical ? jobHistoryUrl : jobStateUrl
    return axios
        .get(url)
        .then(response => {
            let result = response.data
            result.forEach(function (item, _) {
                item["progress"] = Math.round((item["resultSummary"]["nrOfResultsReceived"] / item["resultSummary"]["nrOfBatchesTotal"]) * 100)
            });
            updateFunc(result)
        }).catch(_ => {
            updateFunc([])
        })
}

function retrieveDataFileInfoAll(returnNSamples) {
    const url = dataFileInfoAllUrl + "?returnNSamples=" + returnNSamples
    return axios
        .get(url)
        .then(response => {
            let dataByType = {}
            response.data.forEach(function (item, _) {
                let returnValue = {}
                let group = item["group"]
                returnValue["isMapping"] = item["isMapping"]
                returnValue["fileName"] = item["fileName"].split("/").pop()
                returnValue["totalNrOfSamples"] = item["totalNrOfSamples"]
                returnValue["jsonDefinition"] = item["jsonDefinition"]
                returnValue["samples"] = item["samples"]
                returnValue["identifier"] = item["identifier"]
                returnValue["description"] = item["description"]
                if (group in dataByType) {
                    dataByType[group].push(returnValue)
                } else {
                    dataByType[group] = [returnValue]
                }
            });
            return dataByType;
        })
        .catch(e => {
            console.log(e)
            return []
        })
}

function retrieveRequestSamplesForData(dataJson, numSamples) {
    const url = parameterValuesSampleRequestUrl + "?returnNSamples=" + numSamples
    const config = {
        headers: {
            "Content-Type" : "application/json"
        }
    }
    return axios
        .post(url, dataJson, config)
        .then(response => {
            console.info("retrieved request sample for passed data")
            console.log(response)
            return response.data
        }).catch(_ => {
            return {}
        })
}

function retrieveAllAvailableIRMetrics(){
    const url = irMetricsAllUrl
    return axios
        .get(url)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

function changeReducedToFullMetricsJsonList(dataObj){
    const url = irMetricsReducedToFullJsonListUrl
    const config = {
        headers: {
            "Content-Type" : "application/json"
        }
    }
    return axios
        .post(url, dataObj, config)
        .then(response => {
            console.info("retrieved list of full ir metric json representations")
            console.log(response)
            return response.data
        }).catch(_ => {
            return []
        })
}

function retrieveServiceUpState() {
    return axios
        .get(appIsUpUrl)
        .then(response => {
            return response.status < 400
        }).catch(_ => {
            return false
        })
}

function retrieveNodeStatus() {
    return axios
        .get(nodeStateUrl)
        .then(response => {
            return response.data.map(worker => {
                let worker_state = {}
                worker_state["avgLoad"] = worker["cpuInfo"]["loadAvg"].toFixed(2)
                worker_state["heapUsage"] = (100 * worker["heapInfo"]["heapUsed"] / worker["heapInfo"]["heapCommited"]).toFixed(2) + "%"
                worker_state["host"] = worker["host"]
                worker_state["port"] = worker["port"]
                worker_state["countCPUs"] = worker["cpuInfo"]["nrProcessors"]
                return worker_state
            });
        }).catch(_ => {
            return []
        })
}

function retrieveTemplatesForType(typeName) {
    return axios
        .get(templateOverviewForTypeUrl + "?type=" + typeName)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

/**
 * Retrieve infos for all available templates for all types
 * (e.g need to have a folder of their name in the templates folder looked in)
 *
 * Result: mapping of templateType -> {templateId -> templateContent (json)}
 */
function retrieveAllAvailableTemplateInfos() {
    return axios
        .get(templateAllTypeToInfoMapUrl)
        .then(response => {
            let data = response.data
            let templateTypes = Object.keys(data)
            let result = {}
            templateTypes.forEach(templateType => {
                result[templateType] = {}
                data[templateType].forEach(info => {
                    result[templateType][info["templateId"]] = JSON.parse(info["content"])
                })
            })
            return result
        }).catch(_ => {
            return {}
        })
}

function retrieveTemplateTypes() {
    return axios
        .get(templateTypeUrl)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

function saveTemplate(templateTypeName, templateName, templateContent) {
    if (templateName === "") {
        console.info("empty template name, not sending for storage")
    }
    return axios
        .post(templateSaveUrl + "?type=" + templateTypeName + "&templateName=" + templateName, templateContent)
        .then(response => {
            console.info("success job template store call")
            console.log(response)
            return true
        })
        .catch(e => {
            console.info("exception on trying to store job template")
            console.log(e)
            return false
        })
}

function executeJob(typeName, jobDefinitionContent) {
    return axios
        .post(templateExecuteUrl + "?type=" + typeName, jobDefinitionContent)
        .then(response => {
            console.info("success job execution call")
            console.log(response)
            return true
        })
        .catch(e => {
            console.info("exception on trying send job execution")
            console.log(e)
            return false
        })
}

function retrieveTemplateContentAndInfo(typeName, templateName) {
    return axios
        .get(templateContentUrl + "?type=" + typeName + "&identifier=" + templateName)
        .then(response => {
            return response.data
        }).catch(_ => {
            return {}
        })
}

function retrieveJobInformation() {
    return axios
        .get(kolibriSearchEvalJobDefinitionUrl)
        .then(response => response.data)
        .catch(_ => {
            return {}
        })
}

export {
    retrieveJobs, retrieveServiceUpState, retrieveNodeStatus,
    retrieveTemplatesForType, retrieveTemplateTypes, saveTemplate, executeJob,
    retrieveTemplateContentAndInfo, retrieveDataFileInfoAll,
    retrieveExecutionIDs, retrieveSingleResultIDsForExecutionID,
    retrieveSingleResultById, retrieveSingleResultByIdFiltered,
    retrieveAnalysisTopFlop, retrieveAnalysisVariance, retrieveRequestSamplesForData,
    retrieveAllAvailableIRMetrics, changeReducedToFullMetricsJsonList,
    retrieveJobInformation, retrieveAllAvailableTemplateInfos
}
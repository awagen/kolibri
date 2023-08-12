import axios, {AxiosResponse} from "axios";
import {
    jobHistoryUrl, jobStateUrl, appIsUpUrl, nodeStateUrl, templateOverviewForTypeUrl,
    templateTypeUrl, templateSaveUrl, templateExecuteUrl,
    templateContentUrl, dataFileInfoAllUrl,
    resultExecutionIdsOverviewUrl, resultExecutionIdsSingleResultsOverviewUrl,
    resultExecutionIdGetDataByIdUrl,
    resultDateIdsUrl,  resultDateIdAndJobIdToResultFiledUrl, resultContentRetrievalUrl,
    resultAnalysisTopFlowUrl, resultAnalysisVarianceUrl, parameterValuesSampleRequestUrl,
    irMetricsAllUrl, irMetricsReducedToFullJsonListUrl, kolibriSearchEvalJobDefinitionUrl,
    templateAllTypeToInfoMapUrl,
    kolibriBaseUrl
} from '../utils/globalConstants'
import _ from "lodash";


class Response {
    success: boolean
    data: any
    msg: string

    constructor(success: boolean, data: any, msg: string) {
        this.success = success
        this.data = data
        this.msg = msg
    }

    static failResponse(msg: string): Response {
        return new Response(false, undefined, msg)
    }

    static successResponse(data: any): Response {
        return new Response(true, data, "")
    }

    static fromAxiosResponse(response: AxiosResponse, msg: string = ""): Response {
        if (response.status >= 400) {
            return this.failResponse(msg)
        }
        return this.successResponse(response.data)
    }

}


/**
 * Retrieve mapping of dates (string formatted as yyyy-mm-dd) to jobIds (array) for which results are available.
 */
function retrieveAvailableResultDateIds() {
    const url = resultDateIdsUrl
    return axios
        .get(url)
        .then(response => {
            return response.data.data
        }).catch(_ => {
            return {}
        })
}

/**
 * Retrieve list of result files corresponding to the passed date and job
 * @param date - date in yyyy-mm-dd format
 * @param job - name of job for which available result files shall be retrieved
 */
function retrieveAvailableResultFilesForDataAndJob(date, job) {
    const url = resultDateIdAndJobIdToResultFiledUrl.replace("#DATE_ID", date).replace("#JOB_ID", job)
    return axios
        .get(url)
        .then(response => {
            return response.data.data
        }).catch(_ => {
            return []
        })
}

/**
 *
 * @param date - date in yyyy-mm-dd format
 * @param job - name of job for which available result files shall be retrieved
 * @param file - the file for the specified date and job to retrieve the content of (json content)
 */
function retrieveResultFileContent(date, job, file) {
    const url = resultContentRetrievalUrl.replace("#DATE_ID", date).replace("#JOB_ID", job).replace("#FILE", file)
    return axios
        .get(url)
        .then(response => {
            return response.data.data
        }).catch(_ => {
            return {}
        })
}


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
            return response.data
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
            "Content-Type": "application/json"
        }
    }
    return axios
        .post(url, data, config)
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
            "Content-Type": "application/json"
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
            let result = response.data.data.map(job => {
                let date = new Date(job.timePlacedInMillis)
                let totalBatchCount = _.sum(Object.values(job.batchCountPerState))
                let finishedBatchesCount = _.sum(
                    Object.keys(job.batchCountPerState)
                        .filter(key => !key.toUpperCase().startsWith("OPEN") && !key.toUpperCase().startsWith("INPROGRESS"))
                        .map(key => job.batchCountPerState[key])
                )
                return {
                    "jobId": job.jobId,
                    "timePlaced": date.toUTCString(),
                    "directives": job.jobLevelDirectives,
                    "batchCountPerState": job.batchCountPerState,
                    "progress": (Math.round(finishedBatchesCount / totalBatchCount) * 100).toFixed(2)
                }
            })
            updateFunc(result)
        }).catch(throwable => {
            console.error(throwable)
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
            "Content-Type": "application/json"
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

function retrieveAllAvailableIRMetrics() {
    const url = irMetricsAllUrl
    return axios
        .get(url)
        .then(response => {
            return response.data
        }).catch(_ => {
            return []
        })
}

function changeReducedToFullMetricsJsonList(dataObj) {
    const url = irMetricsReducedToFullJsonListUrl
    const config = {
        headers: {
            "Content-Type": "application/json"
        }
    }
    return axios
        .post(url, dataObj, config)
        .then(response => {
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
                worker_state["lastUpdate"] = worker["lastUpdate"]
                worker_state["nodeHash"] = worker["nodeId"]
                worker_state["cpuCores"] = worker["cpuInfo"]["numCores"]
                worker_state["cpuLoad"] = worker["cpuInfo"]["cpuLoad"].toFixed(2)
                worker_state["heapUsed"] = worker["heapMemoryInfo"]["used"].toFixed(2)
                worker_state["heapMax"] = worker["heapMemoryInfo"]["max"].toFixed(2)
                worker_state["nonHeapUsed"] = worker["nonHeapMemoryInfo"]["used"].toFixed(2)
                return worker_state
            });
        }).catch(_ => {
            return []
        })
}

function retrieveTemplatesForType(typeName) {
    return axios
        .get(templateOverviewForTypeUrl + "/" + typeName)
        .then(response => {
            return response.data.data
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
            let templateTypes = Object.keys(data.data)
            let result = {}
            templateTypes.forEach(templateType => {
                result[templateType] = {}
                data.data[templateType].forEach(info => {
                    result[templateType][info["templateId"]] = info["content"]
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
            return response.data.data
        }).catch(_ => {
            return []
        })
}

function executeGetRequest(url): Promise<AxiosResponse> {
    return axios.get(url)
}

function executePostRequest(url, bodyContent): Promise<AxiosResponse> {
    return axios.post(url, bodyContent)
}

function executePutRequest(url, bodyContent): Promise<AxiosResponse> {
    return axios.put(url, bodyContent)
}

function axiosRequestToJsonString(request: XMLHttpRequest): string {
    if (request !== null) {
        return JSON.stringify({
            "status": request.status,
        })
    }
    return "{}"
}

function handleResponse(responsePromise: Promise<AxiosResponse>): Promise<Response> {
    return responsePromise
        .then(response => {
            return Response.fromAxiosResponse(response)
        })
        .catch(function (error) {
            if (error.response) {
                // Request made and server responded
                return Response.failResponse(error.response.data)
            } else if (error.request) {
                // The request was made but no response was received
                // host protocol method path
                return Response.failResponse(`request failed - status: ${axiosRequestToJsonString(error.request)}`)
            } else {
                // Something happened in setting up the request that triggered an Error
                return Response.failResponse(error.message)
            }
        })
}

function executeAndHandleGetRequest(url): Promise<Response> {
    return handleResponse(executeGetRequest(url))
}

function executeAndHandlePostRequest(url, bodyContent): Promise<Response> {
    return handleResponse(executePostRequest(url, bodyContent))
}

function executeAndHandlePutRequest(url, bodyContent): Promise<Response> {
    return handleResponse(executePutRequest(url, bodyContent))
}

function saveTemplate(templateTypeName, templateName, templateContent) {
    if (templateName === "") {
        return Response.failResponse("empty template name, not sending for storage")
    }
    let url = templateSaveUrl + "/" + templateTypeName + "/" + templateName
    return executeAndHandlePostRequest(url, templateContent)
}

/**
 * Generic function allowing posting of json content against a passed endpoint.
 * Mainly used to post job definitions to job-specific endpoints to execute the jobs.
 * @param endpoint
 * @param jsonContent
 */
function postAgainstEndpoint(endpoint, jsonContent) {
    let url = `${kolibriBaseUrl}/${endpoint}`
    return executeAndHandlePostRequest(url, jsonContent)
}

/**
 * Executing job definitions, yet posting to a generic endpoint.
 * Thus here is no differentiation which endpoint holds for which execution.
 * TODO: should be replaceable as soon as all job definitions come with their specific endpoints,
 * which would make a generic one obsolete
 * @param typeName
 * @param jobDefinitionContent
 */
function executeJob(typeName, jobDefinitionContent) {
    let url = templateExecuteUrl
    return executeAndHandlePostRequest(url, jobDefinitionContent)
}

function retrieveTemplateContentAndInfo(typeName, templateName) {
    return axios
        .get(templateContentUrl + "/" + typeName + "/" + templateName)
        .then(response => {
            return response.data.data
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
    retrieveSingleResultById,
    retrieveAnalysisTopFlop, retrieveAnalysisVariance, retrieveRequestSamplesForData,
    retrieveAllAvailableIRMetrics, changeReducedToFullMetricsJsonList,
    retrieveJobInformation, retrieveAllAvailableTemplateInfos,
    postAgainstEndpoint
}
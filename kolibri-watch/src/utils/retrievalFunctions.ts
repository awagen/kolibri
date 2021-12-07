import axios from "axios";
import {jobHistoryUrl, jobStateUrl, appIsUpUrl, nodeStateUrl, templateOverviewForTypeUrl,
    templateTypeUrl, templateSaveUrl, templateExecuteUrl,
    templateContentUrl} from '../utils/globalConstants'


function retrieveJobs(historical, updateFunc) {
    const  url = historical ? jobHistoryUrl : jobStateUrl
    return axios
        .get(url)
        .then(response => {
            let result = response.data
            result.forEach(function (item, index) {
                item["progress"] = Math.round((item["resultSummary"]["nrOfResultsReceived"] / item["resultSummary"]["nrOfBatchesTotal"]) * 100)
            });
            updateFunc(result)
        }).catch(_ => {
            updateFunc([])
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

export {retrieveJobs, retrieveServiceUpState, retrieveNodeStatus,
    retrieveTemplatesForType, retrieveTemplateTypes, saveTemplate, executeJob,
    retrieveTemplateContentAndInfo}
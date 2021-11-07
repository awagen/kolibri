import axios from "axios";
import {jobHistoryUrl, jobStateUrl, appIsUpUrl, nodeStateUrl} from '../utils/globalConstants'


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
                worker_state["avgCpuUsage"] = (100 * worker["cpuInfo"]["loadAvg"] / worker["cpuInfo"]["nrProcessors"]).toFixed(2) + "%"
                worker_state["heapUsage"] = (100 * worker["heapInfo"]["heapUsed"] / worker["heapInfo"]["heapMax"]).toFixed(2) + "%"
                worker_state["host"] = worker["host"]
                worker_state["port"] = worker["port"]
                worker_state["countCPUs"] = worker["cpuInfo"]["nrProcessors"]
                return worker_state
            });
        }).catch(_ => {
            return []
        })
}

export {retrieveJobs, retrieveServiceUpState, retrieveNodeStatus}
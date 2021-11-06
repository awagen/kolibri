import {createApp} from 'vue'
import {createStore} from 'vuex'
import App from './App.vue'
import './index.css'
import router from './router'
import {appIsUpUrl, jobHistoryUrl, jobStateUrl, nodeStateUrl} from './utils/globalConstants'
import axios from "axios";

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';

// TODO: move this function to some lib, cant def this within the mutations
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

// Create a new store instance.
const store = createStore({
    state() {
        return {
            serviceIsUp: false,
            statusRefreshIntervalInMs: 2000,
            runningNodes: [],
            runningJobs: [],
            jobHistory: []
        }
    },

    mutations: {
        updateServiceUpState(state) {
            return axios
                .get(appIsUpUrl)
                .then(response => {
                    state.serviceIsUp = response.status < 400
                }).catch(_ => {
                    state.serviceIsUp = false
                })
        },

        retrieveNodeStatus(state) {
            return axios
                .get(nodeStateUrl)
                .then(response => {
                    state.runningNodes = response.data.map(worker => {
                        let worker_state = {}
                        worker_state["avgCpuUsage"] = (100 * worker["cpuInfo"]["loadAvg"] / worker["cpuInfo"]["nrProcessors"]).toFixed(2) + "%"
                        worker_state["heapUsage"] = (100 * worker["heapInfo"]["heapUsed"] / worker["heapInfo"]["heapMax"]).toFixed(2) + "%"
                        worker_state["host"] = worker["host"]
                        worker_state["port"] = worker["port"]
                        worker_state["countCPUs"] = worker["cpuInfo"]["nrProcessors"]
                        return worker_state
                    });
                }).catch(_ => {
                    state.runningNodes = []
                })
        },

        retrieveRunningJobs(state) {
            return retrieveJobs( false, x => {
                console.info("updating running jobs")
                console.log(x)
                state.runningJobs = x
            })
        },

        retrieveJobHistory(state) {
            return retrieveJobs( true, x => {
                console.info("updating jobHistory")
                console.log(x)
                state.jobHistory = x
            })
        }

    }
})

// initial service status check
store.commit("updateServiceUpState")
store.commit("retrieveNodeStatus")
store.commit("retrieveRunningJobs")
store.commit("retrieveJobHistory")
// regular scheduling
window.setInterval(() => {
    store.commit("updateServiceUpState")
    store.commit("retrieveNodeStatus")
    store.commit("retrieveRunningJobs")
    store.commit("retrieveJobHistory")
}, store.state.statusRefreshIntervalInMs)


const app = createApp(App);
app.use(router)
// https://next.vuex.vuejs.org/guide/
app.use(store)
app.mount('#app');

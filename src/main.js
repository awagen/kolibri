import {createApp} from 'vue'
import {createStore} from 'vuex'
import App from './App.vue'
import './index.css'
import router from './router'
import {retrieveJobs, retrieveNodeStatus, retrieveServiceUpState} from './utils/retrievalFunctions'

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';

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
            retrieveServiceUpState().then(response => state.serviceIsUp = response)
        },

        updateNodeStatus(state) {
            retrieveNodeStatus().then(response => state.runningNodes = response)
        },

        updateRunningJobs(state) {
            return retrieveJobs( false, x => {
                state.runningJobs = x
            })
        },

        updateJobHistory(state) {
            return retrieveJobs( true, x => {
                state.jobHistory = x
            })
        }

    }
})

// initial service status check
store.commit("updateServiceUpState")
store.commit("updateNodeStatus")
store.commit("updateRunningJobs")
store.commit("updateJobHistory")
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

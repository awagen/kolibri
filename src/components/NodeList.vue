<template>

  <h2 class="runningJobHeader">NODES</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Host</th>
      <th>Port</th>
      <th>CPUs</th>
      <th>CPU Usage</th>
      <th>Heap Usage</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running nodes -->
    <tr v-for="node in runningNodes">
      <td>{{node.host}}</td>
      <td>{{node.port}}</td>
      <td>{{node.countCPUs}}</td>
      <td>{{node.avgCpuUsage}}</td>
      <td>{{node.heapUsage}}</td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";
import { nodeStateUrl } from '../utils/globalConstants'

export default {
  props: {
  },
  setup() {
    const runningNodes = ref([])
    const runningNodesRefreshIntervalInMs = ref(5000)

    function retrieveNodeStatus() {
      return axios
          .get(nodeStateUrl)
          .then(response => {
            runningNodes.value = response.data.map(worker => {
              let worker_state = {}
              worker_state["avgCpuUsage"] = (100 * worker["cpuInfo"]["loadAvg"] / worker["cpuInfo"]["nrProcessors"]).toFixed(2) + "%"
              worker_state["heapUsage"] = (100 * worker["heapInfo"]["heapUsed"] / worker["heapInfo"]["heapMax"]).toFixed(2) + "%"
              worker_state["host"] = worker["host"]
              worker_state["port"] = worker["port"]
              worker_state["countCPUs"] = worker["cpuInfo"]["nrProcessors"]
              return worker_state
            });
          }).catch(_ => {
            runningNodes.value = []
          })
    }

    onMounted(() => {
      // execute once initially to fill display
      retrieveNodeStatus()

      // execute scheduled in intervals of given length to refresh display
      window.setInterval(() => {
        retrieveNodeStatus()
      }, runningNodesRefreshIntervalInMs.value)
    })

    onBeforeUnmount(() => {
      clearInterval()
    })

    return {
      runningNodes
    }
  }

}

</script>

<style scoped>

table {
  font-size: medium;
  color: #9C9C9C;
  border-color: darkgrey;
}

tbody > tr, thead {
  background-color: #233038;
}

th, td {
  border-color: black !important;
}

td, th {
  border-bottom: none !important;
}

.btn.kill {
  background-color: #5c0003;
  color: #9C9C9C ;
  border: none;
}

.runningJobHeader {
  padding-top: 1em;
}

</style>
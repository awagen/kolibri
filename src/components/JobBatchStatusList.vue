<template>

  <h2 class="runningBatchesHeader">{{header}}</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Job Name</th>
      <th>Node</th>
      <th>Batch ID</th>
      <th>Elements Processed</th>
      <th>Total Elements</th>
      <th>Progress</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running jobs -->
    <tr v-for="job in runningBatchStates">
      <td>{{job.jobId}}</td>
      <td>{{job.node}}</td>
      <td>{{job.batchId}}</td>
      <td>{{job.totalProcessed}}</td>
      <td>{{job.totalToProcess}}</td>
      <td>
        <div class="bar bar-sm">
          <div class="bar-item" role="progressbar" :style="{'width': job.progress + '%'}" aria-valuenow="25" aria-valuemin="0" aria-valuemax="100"></div>
        </div>
      </td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";

export default {
  props: [
    'header',
    'batchRetrievalUrl'
  ],
  setup(props) {
    const runningBatchStates = ref([])
    const runningBatchRefreshIntervalInMs = ref(10000)

    function retrieveRunningBatchStates() {
      return axios
          .get(props.batchRetrievalUrl)
          .then(response => {
            runningBatchStates.value =  response.data
            runningBatchStates.value.forEach(function (item, index) {
              item["progress"] = Math.round((item["totalProcessed"] / item["totalToProcess"]) * 100)
            });
          }).catch(_ => {
            runningBatchStates.value = []
          })
    }

    onMounted(() => {
      // execute once initially to fill display
      retrieveRunningBatchStates()

      // execute scheduled in intervals of given length to refresh display
      window.setInterval(() => {
        retrieveRunningBatchStates()
      }, runningBatchRefreshIntervalInMs.value)
    })

    onBeforeUnmount(() => {
      clearInterval()
    })

    return {
      runningBatchStates
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

.runningBatchesHeader {
  padding-top: 1em;
}

</style>
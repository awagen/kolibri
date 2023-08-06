<template>

  <h2 class="runningBatchesHeader">{{header}}</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Job Name</th>
      <th>BatchNr</th>
      <th>LastUpdate</th>
      <th>Status</th>
      <th>Node</th>
      <th>Elements Processed</th>
      <th>Total Elements</th>
      <th>Progress</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running jobs -->
    <tr v-for="batch in runningBatchStates">
      <td>{{batch.jobId}}</td>
      <td>{{batch.batchNr}}</td>
      <td>{{batch.lastUpdate}}</td>
      <td>{{batch.processingStatus}}</td>
      <td>{{batch.processingNode}}</td>
      <td>{{batch.numItemsProcessed}}</td>
      <td>{{batch.numItemsTotal}}</td>
      <td>
        <div class="bar bar-sm">
          <div class="bar-item" role="progressbar" :style="{'width': batch.progress + '%'}" aria-valuenow="25" aria-valuemin="0" aria-valuemax="100"></div>
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
            let result = response.data.data.map(batch => {
              let numItemsProcessed = batch.processingInfo.numItemsProcessed
              let numItemsTotal = batch.processingInfo.numItemsTotal
              return {
                "jobId": batch.stateId.jobId,
                "batchNr": batch.stateId.batchNr,
                "lastUpdate": batch.processingInfo.lastUpdate,
                "numItemsProcessed": numItemsProcessed,
                "numItemsTotal": numItemsTotal,
                "processingNode":  batch.processingInfo.processingNode,
                "processingStatus": batch.processingInfo.processingStatus,
                "progress": Math.round(( numItemsProcessed / numItemsTotal) * 100).toFixed(2)
              }
            })
            runningBatchStates.value = result
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
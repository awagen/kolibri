<template>

  <h2 class="runningJobHeader">RUNNING JOBS</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Job Name</th>
      <th>Type</th>
      <th>Start</th>
      <th>Progress</th>
      <th>Action</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running jobs -->
    <tr v-for="job in runningJobs">
      <td>{{job.jobId}}</td>
      <td>{{job.jobType}}</td>
      <td>{{job.startTime}}</td>
      <td>{{job.resultSummary}}</td>
      <td><button @click="killJob(job.jobId)" class="btn btn-primary s-circle kill">Kill</button></td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";

export default {
  props: {
  },
  setup() {
    const runningJobs = ref([])
    const runningJobsRefreshIntervalInMs = ref(10000)

    function retrieveRunningJobs() {
      console.log("executing retrieveRunningJobs")
      return axios
          .get('http://localhost:8000/jobStates')
          .then(response => {
            runningJobs.value =  response.data
          }).catch(_ => {
            runningJobs.value = []
          })
    }

    function killJob(jobId){
      console.log("executing killJob")
      return axios
          .delete('http://localhost:8000/stopJob?jobId=' + jobId)
          .then(response => {
            console.info("killJob response: " + response.data)
          }).catch(e => {
            console.info("exception on stopJob call: ")
            console.log(e)
          })
    }

    onMounted(() => {
      // execute once initially to fill display
      retrieveRunningJobs()

      // execute scheduled in intervals of given length to refresh display
      window.setInterval(() => {
        retrieveRunningJobs()
      }, runningJobsRefreshIntervalInMs.value)
    })

    onBeforeUnmount(() => {
      clearInterval()
    })

    return {
      runningJobs,
      killJob
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
  background-color: #340000;
  color: #9C9C9C ;
  border: none;
}

.btn.kill:hover {
  background-color: #5c0003;
}

.runningJobHeader {
  padding-top: 1em;
}

</style>
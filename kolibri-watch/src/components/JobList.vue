<template>

  <h2 class="runningJobHeader">{{ header }}</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Job Name</th>
      <th>TimePlaced</th>
      <th>Directives</th>
      <th>BatchCount Per Status</th>
      <th>Progress</th>
      <th v-if="showKillButton">Action</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running jobs -->
    <tr v-for="job in data">
      <td>{{ job.jobId }}</td>
      <td>{{ job.timePlaced }}</td>
      <td>{{ job.directives }}</td>
      <td>{{ job.batchCountPerState }}</td>
      <td>
        <div class="bar bar-sm">
          <div class="bar-item" role="progressbar" :style="{'width': job.progress + '%'}" aria-valuenow="25"
               aria-valuemin="0" aria-valuemax="100"></div>
        </div>
      </td>
      <td v-if="showKillButton">
        <button @click="killJob(job.jobId)" class="btn btn-primary s-circle kill">Kill</button>
      </td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted} from "vue";
import axios from "axios";
import {stopJobUrl} from '../utils/globalConstants'

export default {
  props: [
    'header',
    'data',
    'showKillButton'
  ],
  setup(props) {

    function killJob(jobId) {
      console.log("executing killJob")
      return axios
          .delete(stopJobUrl + '?jobId=' + jobId)
          .then(response => {
            console.info("killJob response: " + response.data)
          }).catch(e => {
            console.info("exception on stopJob call: ")
            console.log(e)
          })
    }

    onMounted(() => {
    })
    onBeforeUnmount(() => {
    })

    return {
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
  color: #9C9C9C;
  border: none;
}

.btn.kill:hover {
  background-color: #5c0003;
}

.runningJobHeader {
  padding-top: 1em;
}

</style>
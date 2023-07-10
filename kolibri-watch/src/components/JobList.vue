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
      <td v-if="showKillButton && jobHasStarted(job)">
        <button @click="killJob(job.jobId)" class="btn btn-primary s-circle kill">Kill</button>
      </td>
      <td v-if="!jobHasStarted(job)">
        <button @click="startJob(job.jobId)" class="btn btn-primary s-circle start">Start</button>
      </td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";
import {startJobUrl, stopJobUrl} from '../utils/globalConstants'

export default {
  props: [
    'header',
    'data',
    'showKillButton'
  ],

  setup(props) {

    function jobHasStarted(job) {
      let existingProcessDirectives = job.directives.map(dir => dir.type.trim()).filter(type => {
        type.startsWith("ONLY_NODE") || type.startsWith("PROCESS")
      })
      let hasStarted = existingProcessDirectives !== undefined && existingProcessDirectives.length > 0
      return hasStarted
    }

    /**
     * NOTE: right now kill-job just means removing all job level directives out of the
     * job folder such that no node will pick up any batch of the job.
     * Batches already running will not be terminated as of now (might change later)
     */
    function killJob(jobId) {
      console.log("executing killJob")
      return axios
          .delete(stopJobUrl.replace("#JOB_ID", jobId))
          .then(response => {
            console.info("killJob response: " + response.data)
          }).catch(e => {
            console.info("exception on stopJob call: ")
            console.log(e)
          })
    }

    /**
     * NOTE: start-job right now simply corresponds to writing the PROCESS-directive
     * for the job (into the job's folder). This triggers all nodes to pick up the batches.
     * Right now this does not mean fine-grained control, e.g the directive written will
     * signal start of processing to all connected nodes. More fine-grained control of
     * set directives via UI will be added shortly
     */
    function startJob(jobId) {
      console.log("executing startJob")
      return axios
          .post(startJobUrl.replace("#JOB_ID", jobId), [{"type": "PROCESS"}])
          .then(response => {
            console.info("startJob response: " + response.data)
          }).catch(e => {
            console.info("exception on startJob call: ")
            console.log(e)
          })
    }

    onMounted(() => {
    })
    onBeforeUnmount(() => {
    })

    return {
      killJob,
      startJob,
      jobHasStarted
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

.btn.start {
  background-color: darkgreen;
  color: #9C9C9C;
  border: none;
}

.btn.kill:hover {
  background-color: darkslategray;
}

.runningJobHeader {
  padding-top: 1em;
}

</style>
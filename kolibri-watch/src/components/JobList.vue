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
        <button @click="stopJob(job.jobId)" class="btn btn-primary s-circle kill">Stop</button>
      </td>
      <td v-if="!jobHasStarted(job)">
        <button @click="startJob(job.jobId)" class="btn btn-primary s-circle start">Start</button>
      </td>
      <td>
        <button @click="deleteOpenJob(job.jobId, true)" class="btn btn-primary s-circle kill">Delete</button>
      </td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";
import {jobDeleteUrl, startJobUrl, stopJobUrl} from '../utils/globalConstants'

export default {
  props: [
    'header',
    'data',
    'showKillButton'
  ],

  setup(props) {

    function jobHasStarted(job) {
      let processDirectives = job.directives.map(dir => dir.type.trim())
      console.debug(`All process directives for job ${job.jobId}: ${processDirectives}`)
      let existingProcessDirectives = processDirectives.filter(type => {
        let isMarkedAsProcessOnSingleNode = type.startsWith("ONLY_NODE")
        let isMarkedAsProcessOnAllNodes = type.startsWith("PROCESS")
        return isMarkedAsProcessOnSingleNode || isMarkedAsProcessOnAllNodes
      })
      let hasStarted = existingProcessDirectives !== undefined && existingProcessDirectives.length > 0
      console.debug(`job ${job.jobId} existing process directives: ${existingProcessDirectives}`)
      console.debug(`job ${job.jobId} has started: ${hasStarted}`)
      return hasStarted
    }

    /**
     * NOTE: right now kill-job just means removing all job level directives out of the
     * job folder such that no node will pick up any batch of the job.
     * Batches already running will not be terminated as of now (might change later)
     */
    function stopJob(jobId) {
      console.debug("executing killJob")
      return axios
          .delete(stopJobUrl.replace("#JOB_ID", jobId))
          .then(response => {
            console.debug("killJob response: " + JSON.stringify(response.data))
          }).catch(e => {
            console.debug("exception on stopJob call: ")
            console.debug(e)
          })
    }

    function deleteOpenJob(jobId, isOpenJob) {
      console.debug(`Deleting open job with id ${jobId}`)
      return axios
          .delete(jobDeleteUrl.replace("#JOB_ID", jobId) + "?isOpenJob=" + isOpenJob)
          .then(response => {
            console.debug("delete job response: " + JSON.stringify(response.data))
          }).catch(e => {
            console.debug("exception on job delete call: ")
            console.debug(e)
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
      console.debug("executing startJob")
      return axios
          .post(startJobUrl.replace("#JOB_ID", jobId), [{"type": "PROCESS"}])
          .then(response => {
            console.debug("startJob response: " + response.data)
          }).catch(e => {
            console.debug("exception on startJob call: ")
            console.debug(e)
          })
    }

    onMounted(() => {
    })
    onBeforeUnmount(() => {
    })

    return {
      stopJob,
      startJob,
      deleteOpenJob,
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
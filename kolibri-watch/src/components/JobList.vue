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
      <th>Action</th>
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
      <td v-if="showStartStopOptions && jobHasStarted(job)">
        <Button
          emitted-event-name="stopJob"
          :emitted-event-arguments="{jobId: job.jobId}"
          @stop-job="stopJob"
          button-class="kill"
          button-shape="circle"
          title="Stop"
        />
      </td>
      <td v-if="showStartStopOptions && !jobHasStarted(job)">
        <Button
            emitted-event-name="startJob"
            :emitted-event-arguments="{jobId: job.jobId}"
            @start-job="startJob"
            button-class="start"
            button-shape="circle"
            title="Start"
        />
      </td>
      <td>
        <Button
            emitted-event-name="deleteOpenJob"
            :emitted-event-arguments="{jobId: job.jobId, isOpenJob: !isHistoryView}"
            @delete-open-job="deleteOpenJob"
            button-class="kill"
            button-shape="circle"
            title="Delete"
        />
      </td>
    </tr>
    </tbody>
  </table>

  <!-- Modal to indicate whether actions were successful or failed -->
  <ResponseModal
      :show="showModal"
      :mode="mode"
      @responseModalClosed="responseModalClosedHandler"
      @responseModalOpened="responseModalOpenedHandler"
      :modal-title="modalTitle"
      :main-content="mainContent"
      :footer-content="footerContent"
      :fade-out-ok='true'
  />

</template>

<script>
import {onBeforeUnmount, onMounted, ref} from "vue";
import {jobDeleteUrl, startJobUrl, stopJobUrl} from '../utils/globalConstants'
import Button from "@/components/partials/controls/Button.vue";
import ResponseModal from "@/components/partials/ResponseModal.vue";
import {axiosCall} from "@/utils/retrievalFunctions";

export default {
  components: {ResponseModal, Button},
  props: [
    'header',
    'data',
    'showStartStopOptions',
    'isHistoryView'
  ],

  setup(props) {

    // response-modal-related attributes
    let showModal = ref(false)
    let modalTitle = ref("")
    let mainContent = ref("")
    let footerContent = ref("")
    let mode = ref("k-success")

    // start: response-modal-related control functions
    function responseModalClosedHandler() {
      showModal.value = false
    }

    function responseModalOpenedHandler() {
      showModal.value = true
    }

    function prepareOKResponseShow() {
      modalTitle.value = ""
      mainContent.value = ""
      mode.value = "k-success"
    }

    function prepareOKResponseShowAndShow() {
      prepareOKResponseShow()
      showModal.value = true
    }

    function prepareErrorResponseShow(title, description) {
      modalTitle.value = title
      mainContent.value = description
      mode.value = "k-fail"
    }

    function prepareErrorResponseShowAndShow(title, description) {
      prepareErrorResponseShow(title, description)
      showModal.value = true
    }
    // end: response-modal-related control functions


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
    function stopJob({jobId}) {
      let url = stopJobUrl.replace("#JOB_ID", jobId)
      axiosCall(
          url,
          "DELETE",
          undefined,
          resp => {
            if (resp.success) prepareOKResponseShowAndShow()
            else prepareErrorResponseShowAndShow("Stop Job Fail", resp.msg)
          }
      )
    }

    function deleteOpenJob({jobId, isOpenJob}) {
      let url = jobDeleteUrl.replace("#JOB_ID", jobId) + "?isOpenJob=" + isOpenJob
      axiosCall(
          url,
          "DELETE",
          undefined,
          resp => {
            if (resp.success) prepareOKResponseShowAndShow()
            else prepareErrorResponseShowAndShow("Delete Job Fail", resp.msg)
          }
      )
    }

    /**
     * NOTE: start-job right now simply corresponds to writing the PROCESS-directive
     * for the job (into the job's folder). This triggers all nodes to pick up the batches.
     * Right now this does not mean fine-grained control, e.g the directive written will
     * signal start of processing to all connected nodes. More fine-grained control of
     * set directives via UI will be added shortly
     */
    function startJob({jobId}) {
      let url = startJobUrl.replace("#JOB_ID", jobId)
      axiosCall(
          url,
          "POST",
          [{"type": "PROCESS"}],
          resp => {
            if (resp.success) prepareOKResponseShowAndShow()
            else prepareErrorResponseShowAndShow("Start Job Fail", resp.msg)
          }
      )
    }

    onMounted(() => {
    })
    onBeforeUnmount(() => {
    })

    return {
      showModal,
      responseModalClosedHandler,
      responseModalOpenedHandler,
      modalTitle,
      mainContent,
      footerContent,
      mode,
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


.runningJobHeader {
  padding-top: 1em;
}

</style>
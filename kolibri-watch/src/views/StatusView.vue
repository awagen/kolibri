<template>
  <NodeList/>
  <JobList :data="this.$store.state.serviceState.runningJobs" :show-start-stop-options="true" :is-history-view="false" header="OPEN JOBS"/>
  <JobBatchStatusList :batch-retrieval-url="retrieveBatchStateUrl" header="BATCH STATUS"/>
</template>

<script>
import JobList from '../components/JobList.vue'
import NodeList from "../components/NodeList.vue";
import {jobStateUrl, jobBatchStateUrl} from '../utils/globalConstants'
import JobBatchStatusList from "../components/JobBatchStatusList.vue";

export default {

  components: {
    JobBatchStatusList,
    NodeList,
    JobList
  },
  data() {
    return {
      retrieveJobStateUrl: jobStateUrl,
      retrieveBatchStateUrl: jobBatchStateUrl
    }
  },
  setup() {
    return {}
  }
}
</script>

<style scoped>

</style>
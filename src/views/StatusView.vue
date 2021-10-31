<template>
  <NodeList/>
  <JobList :job-retrieval-url="retrieveJobStateUrl" :showKillButton="true"
           header="RUNNING JOBS"/>
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
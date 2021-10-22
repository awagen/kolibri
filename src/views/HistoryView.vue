<template>
  <JobList :job-retrieval-url="retrieveFinishedJobStateUrl" :showKillButton="false"
           header="COMPLETED JOBS"/>
</template>

<script>
import JobList from '../components/JobList.vue'
import {jobHistoryUrl} from "../utils/globalConstants";

export default {

  components: {
    JobList,
  },
  data() {
    return {
      retrieveFinishedJobStateUrl: jobHistoryUrl
    }
  },
  setup() {
    return {}
  }
}
</script>

<style scoped>

</style>
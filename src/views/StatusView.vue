<template>
  <NodeList/>
  <JobList :job-retrieval-url="retrieveJobStateUrl" :showKillButton="true"
           header="RUNNING JOBS"/>
</template>

<script>
import JobList from '../components/JobList.vue'
import NodeList from "../components/NodeList.vue";
import {jobStateUrl} from '../utils/globalConstants'

export default {

  components: {
    NodeList,
    JobList
  },
  data() {
    return {
      retrieveJobStateUrl: jobStateUrl
    }
  },
  setup() {
    return {}
  }
}
</script>

<style scoped>

</style>
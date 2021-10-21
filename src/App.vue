<template>
  <header class="navbar">
    <section class="navbar-section">
      <a href="#" class="btn btn-link">DOCS</a>
      <a href="#" class="btn btn-link">EXAMPLES</a>
      <a href="#" class="btn btn-link">GITHUB</a>
    </section>
    <section class="navbar-center">
      <!-- centered logo or brand -->
      <img id="menu-logo" alt="Kolibri logo" src="./assets/images/kolibri.svg"/>
    </section>
    <section class="navbar-section">
      <a href="#" class="btn btn-link">CREATE</a>
    </section>
  </header>

  <!-- divider element -->
  <div class="divider"></div>
  <ServiceStatus/>
  <NodeList/>
  <JobList :job-retrieval-url="retrieveJobStateUrl" :showKillButton="true"
           header="RUNNING JOBS"/>
  <JobList :job-retrieval-url="retrieveFinishedJobStateUrl" :showKillButton="false"
           header="COMPLETED JOBS"/>
</template>

<script>
import JobList from './components/JobList.vue'
import ServiceStatus from "./components/ServiceStatus.vue";
import NodeList from "./components/NodeList.vue";
import {jobHistoryUrl, jobStateUrl} from './utils/globalConstants'

export default {

  components: {
    NodeList,
    JobList,
    ServiceStatus
  },
  data() {
    return {
      retrieveJobStateUrl: jobStateUrl,
      retrieveFinishedJobStateUrl: jobHistoryUrl
    }
  },
  setup() {
    return {}
  }
}
</script>

<style scoped>
#menu-logo {
  width: 7em;
  filter: invert(40%) sepia(3%) saturate(2476%) hue-rotate(86deg) brightness(118%) contrast(119%);
}

.navbar {
  padding: 1em 8em 0.2em;
}

.btn.btn-link {
  color: #9C9C9C;
  font-size: x-large;
}

.btn-link:hover {
  color: black !important;
}

.divider {
  border-color: #353535;
}

.navbar-section a {
  padding-right: 2em;
}

</style>
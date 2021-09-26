<template>
<!--  <svg id="dataviz_area" height=200 width=450>-->
<!--    <circle r="25" cx="100" cy="100" fill="purple;"></circle>-->
<!--  </svg>-->

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
      <td>{{job.name}}</td>
      <td>{{job.type}}</td>
      <td>{{job.startTime}}</td>
      <td>{{job.process}}</td>
      <td><button class="btn btn-primary s-circle kill">Kill</button></td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import * as d3 from "d3";
import {computed, onBeforeUnmount, onMounted, ref} from "vue";
import axios from "axios";

export default {
  props: {
    data: Array[String]
  },
  setup(props) {
    const runningJobs = ref([])
    const runningJobsRefreshIntervalInMs = ref(10000)

    function retrieveRunningJobs() {
      console.log("executing retrieveRunningJobs")
      return axios
          .get('http://localhost:8000/runningJobs')
          .then(response => {
            runningJobs.value =  JSON.parse(response.data)
          })
    }

    const computedValue = computed(() => {
      return props.data[0];
    })

    onMounted(() => {
      // let svg = d3.select("#dataviz_area");
      // svg.append("circle")
      //     .attr("cx", 120)
      //     .attr("cy", 120)
      //     .attr("r", 40)
      //     .style("fill", "blue");

      // runningJobs.value = [
      //   {"name": "job1", "type": "Parameter Optimization", "startTime": "14 October 1994"},
      //   {"name": "job2", "type": "Parameter Optimization", "startTime": "15 October 1994"},
      //   {"name": "job3", "type": "Parameter Optimization", "startTime": "16 October 1994"}
      // ]
      //
      // console.log("data: " + props.data.valueOf())
      // console.log("first: " + computedValue.value)

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
      computedValue
    }
  }

}

</script>

<style scoped>
#dataviz_area {
  width: 100%;
}

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
  background-color: #5c0003;
  color: #9C9C9C ;
  border: none;
}

.runningJobHeader {
  padding-top: 1em;
}

</style>
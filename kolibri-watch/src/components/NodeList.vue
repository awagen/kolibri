<template>

  <h2 class="nodeListHeader">NODES</h2>
  <table class="table">
    <thead>
    <tr>
      <th>LastUpdate</th>
      <th>Node</th>
      <th>CpuCores</th>
      <th>CpuLoad</th>
      <th>HeapUsed (MB)</th>
      <th>HeapMax (MB)</th>
      <th>NonHeapUsed (MB)</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running nodes -->
    <tr v-for="node in this.$store.state.serviceState.runningNodes">
      <td>{{node.lastUpdate}}</td>
      <td>{{node.nodeHash}}</td>
      <td>{{node.cpuCores}}</td>
      <td>{{node.cpuLoad}}</td>
      <td>{{node.heapUsed}}</td>
      <td>{{node.heapMax}}</td>
      <td>{{node.nonHeapUsed}}</td>
    </tr>
    </tbody>
  </table>
</template>

<script>
import {onBeforeUnmount, onMounted} from "vue";

export default {
  props: {
  },
  setup() {
    onMounted(() => {})
    onBeforeUnmount(() => {
      clearInterval()
    })
    return {
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

.nodeListHeader {
  padding-top: 1em;
}

</style>
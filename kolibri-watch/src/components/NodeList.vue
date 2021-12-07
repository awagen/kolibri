<template>

  <h2 class="nodeListHeader">NODES</h2>
  <table class="table">
    <thead>
    <tr>
      <th>Host</th>
      <th>Port</th>
      <th>CPUs</th>
      <th>Load</th>
      <th>Heap Usage</th>
    </tr>
    </thead>
    <tbody>
    <!-- list all running nodes -->
    <tr v-for="node in this.$store.state.runningNodes">
      <td>{{node.host}}</td>
      <td>{{node.port}}</td>
      <td>{{node.countCPUs}}</td>
      <td>{{node.avgLoad}}</td>
      <td>{{node.heapUsage}}</td>
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
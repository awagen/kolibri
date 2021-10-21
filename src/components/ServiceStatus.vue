<template>
  <div class="connection-status-container">
    <span class="connection-status-text">Status:</span>
    <span v-bind:class="{ connected: isConnected }"
          class="s-circle connection-status">
  </span>
  </div>
</template>


<style>

.connection-status-container {
  padding-top: 0.2em;
  width: 6em;
  line-height: 1.2em;
}

.connection-status-text {
  height: 100%;
  vertical-align: middle;
  line-height: 1.0em;
}

.connection-status {
  background-color: indianred;
  opacity: 0.5;
  width: 1.0em;
  height: 1.0em;
  margin-left: 0.5em;
  display: inline-block;
  vertical-align: middle;
}

.connection-status.connected {
  background-color: lightgreen;
}

</style>

<script>
import axios from "axios";
import {onMounted, ref} from "vue";
import { appIsUpUrl } from '../utils/globalConstants'


export default {
  props: {},
  setup() {
    const isConnected = ref(Boolean)
    const statusRefreshIntervalInMs = ref(5000)

    function retrieveServiceStatus() {
      console.log("executing retrieveServiceStatus")
      return axios
          .get(appIsUpUrl)
          .then(response => {
            isConnected.value = response.status < 400
          }).catch(_ => {
            isConnected.value = false
          })
    }

    onMounted(() => {
      // initial service status check
      retrieveServiceStatus()
      // regular scheduling
      window.setInterval(() => {
        retrieveServiceStatus()
      }, statusRefreshIntervalInMs.value)
    })

    return {
      isConnected
    }
  }
}

</script>
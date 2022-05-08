<template>
  <div class="connection-status-container">
    <span class="connection-status-text">Status:</span>
    <span v-bind:class="{ connected: this.$store.state.serviceState.serviceIsUp }"
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
import {onMounted} from "vue";

export default {
  props: {},
  setup() {
    onMounted(() => {})
    return {}
  }
}

</script>
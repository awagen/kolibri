<template>
  <div class="form-container-experiment-create columns">
    <form class="form-horizontal col-6 column">
      <h3 class="title">
        Definition Creation
      </h3>
      <!-- form group for job name -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="job-name-1">JobName</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input" type="text" id="job-name-1" placeholder="JobName">
        </div>
      </div>

      <!-- form group for requested nr of parallel tasks -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="tasks-1">Parallel Batches</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input" type="text" id="tasks-1" placeholder="ParallelBatches">
        </div>
      </div>

      <!-- form group for requested context path -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="path-1">ContextPath</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input" type="text" id="path-1" placeholder="ContextPath">
        </div>
      </div>

      <!-- form group for fixed params -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="fixed-params-1">FixedParams</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input" type="text" id="fixed-params-1" placeholder="FixedParams">
        </div>
      </div>

      <!-- -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="job-type-1">JobType</label>
        </div>
        <div class="col-9 col-sm-12">
          <select class="form-input" id="job-type-1">
            <option>Choose a job type</option>
            <option>SearchEvaluation</option>
          </select>
        </div>
      </div>
      <!-- add button for connections -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="connection-add-1">Add Connection</label>
        </div>
        <div class="col-9 col-sm-12">
          <button v-if="connection_button_expanded" type='button' @click="toggle_connection_add_button()"
                  class="k-form-add btn btn-action s-circle" id="connection-add-1"><i class="icon icon-arrow-down"></i>
          </button>
          <button v-else type='button' @click="toggle_connection_add_button()"
                  class="k-form-add btn btn-action s-circle" id="connection-add-1"><i class="icon icon-arrow-up"></i>
          </button>
        </div>
      </div>
      <!-- add fields for connection -->
      <div v-if="connection_button_expanded" class="form-group" id="connection-add-host">
        <div class="col-3 col-sm-12"></div>
        <div class="col-3 col-sm-12">
          <label class="form-label" for="connection-host">Host</label>
        </div>
        <div class="col-6 col-sm-12">
          <input class="form-input" type="text" id="connection-host" placeholder="Host">
        </div>
        <div class="form-separator"></div>
        <!-- port -->
        <div class="col-3 col-sm-12"></div>
        <div class="col-3 col-sm-12">
          <label class="form-label" for="connection-port">Port</label>
        </div>
        <div class="col-6 col-sm-12">
          <input class="form-input" type="text" id="connection-port" placeholder="Port">
        </div>
        <div class="form-separator"></div>
        <!-- use https selection -->
        <div class="col-3 col-sm-12"></div>
        <div class="col-3 col-sm-12">
          <label class="form-label">Https</label>
        </div>
        <div class="col-6 col-sm-12">
          <label class="form-switch">
            <input type="checkbox" id="connection-https">
            <i class="form-icon"></i>
          </label>
        </div>
        <!-- submit button -->
        <div class="form-separator"></div>
        <div class="col-3 col-sm-12"></div>
        <div class="col-9 col-sm-12">
          <button type='button' @click="add_connection()" class="k-form-add btn btn-action" id="connection-submit-1">
            SUBMIT
          </button>
        </div>
      </div>
    </form>
    <!-- Other half is the status display for stuff already added -->
    <div class="col-6 column k-json-panel">
      <h3 class="title">
        JSON
      </h3>
      <pre v-html="created_json_string_state"/>
    </div>
  </div>
</template>

<script>
import {onMounted, ref} from "vue";
import {objectToJsonStringAndSyntaxHighlight} from "../utils/formatFunctions";

export default {

  props: [],
  setup(props) {
    const hostInputSelector = "connection-host"
    const portInputSelector = "connection-port"
    const httpsCheckboxSelector = "connection-https"
    const connection_button_expanded = ref(false)

    const connections = ref([])
    const created_state = ref({})
    const created_json_string_state = ref("")

    function toggle_connection_add_button() {
      connection_button_expanded.value = !connection_button_expanded.value
    }

    function add_connection() {
      let host = document.getElementById(hostInputSelector).value
      let port = document.getElementById(portInputSelector).value
      let https = document.getElementById(httpsCheckboxSelector).checked
      let connection = {
        "host": host,
        "port": port,
        "useHttps": https
      }
      connections.value.push(connection)
      created_state.value["connections"] = connections.value
      created_json_string_state.value = objectToJsonStringAndSyntaxHighlight(created_state.value) //syntaxHighlight(JSON.stringify(created_state.value, null, '\t'))
      console.log(connections.value)
      console.log(created_state.value)
    }

    onMounted(() => {
    })

    return {
      connection_button_expanded,
      toggle_connection_add_button,
      add_connection,
      created_state,
      created_json_string_state
    }
  },


}

</script>

<style scoped>

.form-container-experiment-create {

  margin-top: 3em;

}

.column {
  padding: .4rem 0 0;
}

.title {
  margin-bottom: 1em;
  text-align: center;
}

.k-form-add.btn {
  /*width: 50%;*/
  padding: 0;
  margin: 0;
  display: block;
  background-color: #9999;
  color: black;
  border-width: 0;
}

.form-separator {
  height: 2.8em;
}

button#connection-submit-1 {
  width: auto;
  background-color: #9999;
  border-width: 0;
  color: black;
  padding: 0.5em;
}

/* need some deep selectors here since otherwise code loaded in v-html directive doesnt get styled */
::v-deep(pre) {
  /*outline: 1px solid #ccc; */
  padding-left: 2em;
  padding-top: 0;
  margin: 5px;
  text-align: left;
}

::v-deep(.string) {
  color: green;
}

::v-deep(.number) {
  color: darkorange;
}

::v-deep(.boolean) {
  color: black;
}

::v-deep(.null) {
  color: magenta;
}

::v-deep(.key) {
  color: #9c9c9c;
}

</style>

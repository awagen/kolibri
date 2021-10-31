<template>
  <div class="form-container-experiment-create columns">
    <form class="form-horizontal col-6 column">
      <h3 class="title">
      Definition Creation
      </h3>
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="job-name-1">JobName</label>
        </div>
        <div class="col-9 col-sm-12">
          <input class="form-input" type="text" id="job-name-1" placeholder="JobName">
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
            <option>LoadTest</option>
          </select>
        </div>
      </div>
      <!-- add button for connections -->
      <div class="form-group">
        <div class="col-3 col-sm-12">
          <label class="form-label" for="connection-add-1">Add Connection</label>
        </div>
        <div class="col-9 col-sm-12">
          <button class="k-form-add btn btn-lg" id="connection-add-1">Add Connections</button>
        </div>
      </div>
    </form>
    <!-- Other half is the status display for stuff already added -->
    <div class="col-6 column">
      <h3 class="title">
        Created
      </h3>
    </div>
  </div>
</template>

<script>
import {onMounted, ref} from "vue";
import axios from "axios";

export default {

  props: [],
  setup(props) {
    const json = ref(JSON.stringify({"data": "val1", "p": "y"}, null, '\t'))
    onMounted(() => {
    })

    return {
      json
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
  width: 50%;
  padding: 0;
  margin: 0;
  display: block;
}

</style>

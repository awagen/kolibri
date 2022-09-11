<template>

  <!-- if available, display some field description here -->
  <template v-if="description !== undefined && description !== ''">
    <div class="popover popover-right">
      <button class="btn btn-action s-circle"><i class="icon icon-message"></i></button>
      <div class="popover-container">
        <div class="card">
          <div class="card-header">
            <div>
              <b>Description</b> <br>
              {{ description }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </template>

</template>

<script>

export default {

  props: {
    description: {type: String, required: false}
  },
  setup(props, context) {
    return {}
  }

}

</script>

<style scoped>

/* popover styling */
.popover, .popover-right, .popover-container {
  overflow: visible;
  margin: 0.2em;
}

.popover button {
  background-color: #588274;
  border-width: 0;
  margin: 0.3em;
}

.card {
  white-space: break-spaces;
}

</style>
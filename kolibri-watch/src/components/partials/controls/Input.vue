<template>

  <div class="col-3 col-sm-12">
    <label class="form-label" :for="inputId">{{label}}</label>
  </div>
  <div class="col-9 col-sm-12">
    <input class="form-input k-value-selector template-name"
           type="text"
           :id="inputId"
           :placeholder="placeHolder">
  </div>

</template>


<script>

export default {

  props: {
    inputId: {
      type: String,
      required: true
    },
    label: {
      type: String,
      required: false,
      default: ""
    },
    placeHolder: {
      type: String,
      required: false,
      default: ""
    }
  }

}

</script>



<style scoped>

input.template-name {
  padding-right: 1em;
  width: 98%;
}


</style>
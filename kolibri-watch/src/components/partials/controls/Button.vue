<template>

  <button type='button'
          @click="this.$emit(emittedEventName)"
          :class="'k-form k-full btn btn-action ' + buttonClass"
          :id="buttonId">
    {{ title }}
  </button>

</template>


<script>

export default {

  props: {
    emittedEventName: {
      type: String,
      required: true,
      description: "Name of event to be emitted to parent on click. Note that the" +
          " parent needs to know how to handle that event"
    },
    buttonClass: {
      type: String,
      required: true,
      description: "Class added to the button classes. Make sure every button class " +
          "comes with the proper styling.",
      validator(value) {
        return ["save", "execute"].includes(value)
      }
    },
    buttonId: {
      type: String,
      required: true
    },
    title: {
      type: String,
      required: false,
      default: ""
    }
  },

  setup(props, context) {

    function emit() {
      context.emit(props.emittedEventName)
    }
  }

}


</script>


<style scoped>

.k-form.btn {
  padding: 0;
  margin: 0;
  display: block;
  background-color: #9999;
  color: black;
  border-width: 0;
}

.k-full.btn {
  width: 98%;
  margin-left: 1em;
  margin-right: 1em;
}

button.save {
  background-color: darkgreen !important;
}

button.execute {
  background-color: orange !important;
}


</style>
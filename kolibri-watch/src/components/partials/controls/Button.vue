<template>

  <button @click.prevent="this.$emit(emittedEventName, emittedEventArguments)"
          :class="'btn btn-primary ' + shapeClass() + ' ' + buttonClass"
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
    emittedEventArguments: {
      type: Object,
      required: false,
      default: {},
      description: "In case of click of the button, we can specify here which additional arguments" +
          " shall be send."
    },
    buttonClass: {
      type: String,
      required: true,
      description: "Class added to the button classes. Make sure every button class " +
          "comes with the proper styling.",
      validator(value) {
        return ["save", "execute", "start", "kill"].includes(value)
      }
    },
    buttonShape: {
      type: String,
      required: true,
      validator(value) {
        return ["circle", "rectangle"].includes(value)
      }
    },
    buttonId: {
      type: String,
      required: false,
      default: ""
    },
    title: {
      type: String,
      required: false,
      default: ""
    }
  },

  setup(props, context) {

    let shapeToClassMapping = {
      "circle": "s-circle",
      "rectangle": "k-full"
    }

    function shapeClass() {
      if (Object.keys(shapeToClassMapping).includes(props.buttonShape)) {
        return shapeToClassMapping[props.buttonShape]
      }
      else return "k-full"
    }

    return {
      shapeClass
    }

  }

}


</script>


<style scoped>

button.k-full {
  width: 98%;
  margin-left: 1em;
  margin-right: 1em;
  background-color: #9999;
  color: black;
  border-width: 0;
}

button.k-full:hover {
  color: lightgrey;
}

button.save {
  background-color: darkgreen !important;
}

button.execute {
  background-color: orange !important;
}

button.kill {
  background-color: #340000;
  color: #9C9C9C;
  border: none;
}

button.kill:hover {
  background-color: darkslategray;
}

button.start {
  background-color: darkgreen;
  color: #9C9C9C;
  border: none;
}

button.start:hover {
  background-color: darkslategray;
}


</style>
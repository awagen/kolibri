<template>

  <input :id=VALUE_INPUT_ID class="form-input metric" type="text" :value="value" @input="updateValueEvent"
         placeholder="Text Input">

  <div :id=TOAST_ID class="toast toast-warning display-none">
    <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
    <span :id=TOAST_CONTENT_ID></span>
  </div>
</template>

<script>
import {ref} from "vue";

export default {

  props: {
    "elementId": String,
    "name": String,
    "regex": String,
    "validationFunction": () => Boolean
  },

  components: {},
  methods: {

  },
  setup(props) {
    let value = ref("")
    let VALUE_INPUT_ID = 'k-' + props.elementId + "-" + 'string-input'
    let TOAST_ID = 'k-' + props.elementId + '-msg-toast'
    let TOAST_CONTENT_ID = 'k-' + props.elementId + '-msg-toast-content'
    let regexStr = (props.regex !== undefined) ? props.regex : ".*"
    let regex = new RegExp(regexStr)

    function validate(val) {
      //return regex.test(val)
      return props.validationFunction(val)
    }

    document.addEventListener('change', function handleClickOutsideBox(event) {
      hideModal()
    });

    function updateValueEvent(valueEvent) {
      console.debug("updated event called with value: " + valueEvent.target.value)
      let updateValue = valueEvent.target.value
      hideModal()
      if (validate(updateValue) || updateValue.trim === "") {
        value.value = updateValue
        context.emit('valueChanged', {name: props.name, value: value.value})
      } else {
        showModalMsg(`value not matching regex '${regexStr}': ${updateValue}`)
        document.getElementById(VALUE_INPUT_ID).value = value.value;
        console.debug("value invalid, no update")
      }
    }

    function showModalMsg(msg) {
      document.getElementById(TOAST_CONTENT_ID).textContent = msg;
      document.getElementById(TOAST_ID).classList.remove("display-none");
    }

    function hideModal() {
      console.debug("hiding")
      document.getElementById(TOAST_ID).classList.add("display-none");
      document.getElementById(TOAST_CONTENT_ID).textContent = "";
    }

    return {
      VALUE_INPUT_ID,
      TOAST_ID,
      TOAST_CONTENT_ID,
      hideModal,
      updateValueEvent,
    }
  }

}

</script>

<style scoped>

.form-input.metric {
  display: inline-block;
}

.display-none {
  display: none;
}

</style>



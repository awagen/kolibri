<template>

  <template v-if="valueType === InputType.INT">
    <input :id=VALUE_INPUT_ID class="form-input metric" type="number" :step=step :value="value" @input="updateValueEvent"
           placeholder="Number Input">
  </template>
  <template v-if="valueType === InputType.FLOAT">
    <input :id=VALUE_INPUT_ID class="form-input metric" type="number" :step=step :value="value" @input="updateValueEvent"
           placeholder="Number Input">
  </template>
  <div :id=TOAST_ID class="toast toast-warning display-none">
    <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
    <span :id=TOAST_CONTENT_ID></span>
  </div>
</template>

<script>
import {ref} from "vue";
import {InputType, InputValidation} from "../../../utils/dataValidationFunctions.ts"

export default {

  props: {
    "name": String,
    "elementId": String,
    "step": Number,
    "valueType": InputType,
    "validationDef": Object
  },
  components: {},
  methods: {
  },
  setup(props, context) {
    let minValue = (props.min != null) ? props.min : 0
    let value = ref(minValue)
    let VALUE_INPUT_ID = 'k-' + props.elementId + "-" + 'number-input'
    let TOAST_ID = 'k-' + props.elementId + '-msg-toast'
    let TOAST_CONTENT_ID = 'k-' + props.elementId + '-msg-toast-content'

    let validator = new InputValidation(props.validationDef)

    function validate(val) {
      console.info("validate of validator called: " + validator.toString())
      return validator.validate(val)
    }

    document.addEventListener('change', function handleClickOutsideBox(event) {
      hideModal()
    });

    function updateValueEvent(valueEvent) {
      console.debug("updated event called with value: " + valueEvent.target.value)
      let updateValue = valueEvent.target.value
      hideModal()
      let validationResult = validate(updateValue)
      if (validationResult.isValid) {
        value.value = updateValue
        // emitting value to communicate to parent name and value
        // of the property.
        // if we traverse this for each element, we can build up all
        // substructures and communicate the changes downstream
        // upstream for the final result
        context.emit('valueChanged', {name: props.name, value: value.value})
      } else {
        showModalMsg(validationResult.failReason)
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
      updateValueEvent,
      hideModal,
      VALUE_INPUT_ID,
      TOAST_ID,
      TOAST_CONTENT_ID,
      InputType
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



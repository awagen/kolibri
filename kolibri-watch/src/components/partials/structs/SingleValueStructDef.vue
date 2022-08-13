<template>
  <template v-if="(elementDef instanceof NumberInputDef)">
    <input :id=VALUE_INPUT_ID class="form-input metric" type="number" :step=elementDef.step :value="value" @input="updateValueEvent"
           placeholder="Number Input">
  </template>
  <template v-if="(elementDef instanceof StringInputDef)">
  <input :id=VALUE_INPUT_ID class="form-input metric" type="text" :value="value" @input="updateValueEvent"
         placeholder="Text Input">
  </template>
  <template v-if="(elementDef instanceof BooleanInputDef)">
    <label class="form-radio form-inline">
      <input type="radio" name="{{elementDef.name}}" :value="true" checked="" @change="updateValueEvent"><i class="form-icon"></i> true
    </label>
    <label class="form-radio form-inline">
      <input type="radio" name="{{elementDef.name}}" :value="false" @change="updateValueEvent"><i class="form-icon"></i> false
    </label>
  </template>
  <div :id=TOAST_ID class="toast toast-warning display-none">
    <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
    <span :id=TOAST_CONTENT_ID></span>
  </div>
  <template v-if="(elementDef instanceof ChoiceInputDef)">
    <template v-for="element in elementDef.choices">
      <label class="form-radio form-inline">
        <input type="radio" name="{{elementDef.name}}" :value="element" checked="" @change="updateValueEvent"><i class="form-icon"></i> {{element}}
      </label>
    </template>
  </template>
  <template v-if="(elementDef instanceof FloatChoiceInputDef)">
    <template v-for="element in elementDef.choices">
      <label class="form-radio form-inline">
        <input type="radio" name="{{elementDef.name}}" :value="element" checked="" @change="updateValueEvent"><i class="form-icon"></i> {{element}}
      </label>
    </template>
  </template>
</template>

<script>
import {ref} from "vue";
import {InputDef, StringInputDef, BooleanInputDef, NumberInputDef, InputType,
ChoiceInputDef, FloatChoiceInputDef} from "../../../utils/dataValidationFunctions.ts"

export default {

  props: {
    "elementDef": InputDef
  },
  components: {},
  methods: {
  },
  setup(props, context) {
    let minValue = (props.elementDef.validation.min != null) ? props.elementDef.validation.min : 0
    let value = ref(minValue)
    let VALUE_INPUT_ID = 'k-' + props.elementDef.elementId + "-" + 'number-input'
    let TOAST_ID = 'k-' + props.elementDef.elementId + '-msg-toast'
    let TOAST_CONTENT_ID = 'k-' + props.elementDef.elementId + '-msg-toast-content'

    let validator = props.elementDef.getInputValidation()

    function validate(val) {
      console.debug("validate of validator called: " + validator.toString())
      return validator.validate(val)
    }

    document.addEventListener('change', function handle(event) {
      if (event.target.id !== VALUE_INPUT_ID) {
        console.info("change event on input id: " + event.target.id)
        return
      }
      console.info(`new value after change event: ${event.target.value}`)
      let validationResult = validate(event.target.value)
      if (validationResult.isValid) {
        hideModal()
      }
    });

    function updateValueEvent(valueEvent) {
      console.debug("updated event called with value: " + valueEvent.target.value)
      let updateValue = valueEvent.target.value
      let validationResult = validate(updateValue)
      if (validationResult.isValid) {
        hideModal()
        value.value = updateValue
        // emitting change event to make parent element react to update / update its structure
        context.emit('valueChanged', {name: props.elementDef.name, value: value.value})
      }
      else {
        showModalMsg(validationResult.failReason)
        console.debug("value invalid")
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
      InputType,
      NumberInputDef,
      StringInputDef,
      BooleanInputDef,
      ChoiceInputDef,
      FloatChoiceInputDef
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

.toast.toast-warning {
  background: linear-gradient(#25333C, #545454);
  border-style: none
}

</style>



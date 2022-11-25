<template>
  <div class="k-input-container">
    <template v-if="(usedElementDef instanceof NumberInputDef)">
      <!-- check if default value is filled, and if so, set value.
      For some reason causes errors when check is baked directly
      into the :value binding below (:value="!!elementDef.defaultValue ? element.defaultValue : null"
      should do but causes input fields to be unusable)-->
      <template v-if="value === 0 || !!value">
        <input :id="getValueInputId()"
               class="form-input metric"
               type="number"
               :step=usedElementDef.step
               :value="value"
               @input="updateValueEvent"
               placeholder="Number Input">
      </template>
      <template v-else>
        <input :id="getValueInputId()"
               class="form-input metric"
               type="number"
               :step=usedElementDef.step
               @input="updateValueEvent"
               placeholder="Number Input">
      </template>
    </template>
    <template v-if="(usedElementDef instanceof StringInputDef)">
      <template v-if="!!value">
        <input :id="getValueInputId()"
               class="form-input metric"
               type="text"
               :value="(!!value) ? value : null"
               @input="updateValueEvent"
               placeholder="Text Input">
      </template>
      <template v-else>
        <input :id="getValueInputId()"
               class="form-input metric"
               type="text"
               @input="updateValueEvent"
               placeholder="Text Input">
      </template>
    </template>
    <template v-if="(usedElementDef instanceof BooleanInputDef)">
      <label class="form-radio form-inline">
        <input :id="getValueInputId()"
               type="radio"
               :name="getValueInputId()"
               :value="true"
               :checked="(value === true) ? '' : null"
               @change="updateValueEvent">
        <i class="form-icon"></i>
        true
      </label>
      <label class="form-radio form-inline">
        <input :id="getValueInputId()"
               type="radio"
               :name="getValueInputId()"
               :value="false"
               :checked="(value === false) ? '' : null"
               @change="updateValueEvent">
        <i class="form-icon"></i>
        false
      </label>
    </template>
    <template v-if="(usedElementDef instanceof ChoiceInputDef)">
      <template v-for="element in usedElementDef.choices">
        <label class="form-radio form-inline">
          <input :id="getValueInputId()"
                 type="radio"
                 :name="getValueInputId()"
                 :value="element"
                 :checked="(value === element) ? '' : null"
                 @change="updateValueEvent">
          <i class="form-icon"></i>
          {{ element }}
        </label>
      </template>
    </template>
    <template v-if="(usedElementDef instanceof FloatChoiceInputDef)">
      <template v-for="element in usedElementDef.choices">
        <label class="form-radio form-inline">
          <input :id="getValueInputId()"
                 type="radio"
                 :name="getValueInputId()"
                 :value="element"
                 :checked="(value === element) ? '' : null"
                 @change="updateValueEvent">
          <i class="form-icon"></i>
          {{ element }}
        </label>
      </template>
    </template>
    <!-- Toast element for warnings / validation messages -->
    <div :id="getToastId()" class="toast toast-warning display-none">
      <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
      <span :id="getToastContentId()"></span>
    </div>
  </div>
</template>

<script>
import {onMounted, onUpdated, ref} from "vue";
import {
  InputDef, StringInputDef, BooleanInputDef, NumberInputDef, InputType,
  ChoiceInputDef, FloatChoiceInputDef
} from "../../../utils/dataValidationFunctions"
import {
  getInputElementToastContentId,
  getInputElementToastId,
  getInputElementId
} from "../../../utils/structDefFunctions";

export default {
  name: "SingleValueStructDef",
  props: {
    elementDef: {
      type: InputDef,
      required: true
    },
    position: {
      type: Number,
      required: false
    },
    name: {
      type: String,
      required: true
    },
    description: {
      type: String,
      required: false
    },
    initWithValue: {
      type: [String, Number, Boolean],
      required: false,
      default: undefined
    }
  },
  // possible situations:
  // a) value passed in initWithValue is confirmed, in which case we only need to send a valueConfirmEvent
  // on which the parent component doesn't need to react,
  // b) send valueChanged with value === undefined, where the parent only
  // needs to update the value presenting that state (e.g if it is a unconditioned field, set all the fields
  // conditioned on it also to undefined),
  // c) valueChanged with an actual value. This needs recalculation on parent end
  // on the valid values
  emits: ['valueChanged', 'valueConfirm'],
  components: {},
  methods: {
  },

  setup(props, context) {

    let usedElementDef = props.elementDef.copy(props.elementDef.elementId)

    let validator = usedElementDef.getInputValidation()

    let value = props.initWithValue

    let convertInputToNumber = (usedElementDef instanceof ChoiceInputDef) &&
        (usedElementDef.choices.filter(choice => isNaN(choice)).length == 0)

    onMounted(() => {
      if (value !== undefined) {
        updateValue(value, true)
      }
    })

    function getValueInputId() {
      return getInputElementId(usedElementDef.elementId, props.name, props.position)
    }

    function getToastId() {
      return getInputElementToastId(usedElementDef.elementId, props.name, props.position)
    }

    function getToastContentId() {
      return getInputElementToastContentId(usedElementDef.elementId, props.name, props.position)
    }

    onUpdated(() => {
      convertInputToNumber = (usedElementDef instanceof ChoiceInputDef) &&
          (usedElementDef.choices.filter(choice => isNaN(choice)).length == 0)
      // note that we need to update the validator here as its possible another elementDef has been passed.
      // if that new def carries another validation, we'd be still using the old, thus the update here.
      // NOTE: do not set possibly changing state in the setup or update it with proper hooks
      validator = usedElementDef.getInputValidation()
    })

    function parseRightType(val) {
      if (usedElementDef.valueType === InputType.INT) {
        return parseInt(val)
      } else if ([InputType.FLOAT, InputType.FLOAT_CHOICE]
          .includes(usedElementDef.valueType)) {
        return parseFloat(val)
      } else if (usedElementDef.valueType === InputType.BOOLEAN) {
        return (val === null || val === undefined) ? val : (val === 'true' || val === true)
      }
      return val
    }

    document.addEventListener('change', function handle(event) {
      if (event.target.id !== getValueInputId()) {
        return
      }
      let validationResult = validator.validate(event.target.value)
      if (validationResult.isValid) {
        hideModal()
      }
    });

    function emitValueConfirmEvent() {
      context.emit('valueConfirm', {name: props.name, value: value, position: props.position})
    }

    function emitValueChangedEvent() {
      context.emit('valueChanged', {name: props.name, value: value, position: props.position})
    }

    function updateValue(newValue, isInitValue) {
      if (newValue === undefined && isInitValue) {
        emitValueConfirmEvent()
        return;
      }
      if (convertInputToNumber) {
        newValue = Number(newValue)
      }
      let validationResult = validator.validate(newValue)
      if (validationResult.isValid) {
        hideModal()
        value = parseRightType(newValue)
        // emitting change event to make parent element react to update / update its structure
        if (isInitValue) {
          emitValueConfirmEvent()
        }
        else {
          emitValueChangedEvent()
        }
      } else {
        value = undefined
        showModalMsg(validationResult.failReason)
        emitValueChangedEvent()
      }
    }

    /**
     * Function for manual edit
     * @param valueEvent
     */
    function updateValueEvent(valueEvent) {
      let newValue = valueEvent.target.value
      updateValue(newValue, false)
    }

    function showModalMsg(msg) {
      document.getElementById(getToastContentId()).textContent = msg;
      document.getElementById(getToastId()).classList.remove("display-none");
    }

    function hideModal() {
      document.getElementById(getToastContentId()).textContent = "";
      document.getElementById(getToastId()).classList.add("display-none");
    }

    return {
      updateValueEvent,
      hideModal,
      getValueInputId,
      getToastId,
      getToastContentId,
      InputType,
      NumberInputDef,
      StringInputDef,
      BooleanInputDef,
      ChoiceInputDef,
      FloatChoiceInputDef,
      value,
      usedElementDef
    }
  }

}

</script>

<style scoped>

.k-input-container {
  overflow-wrap: break-word;
}

label {
  max-width: 100%;
}

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



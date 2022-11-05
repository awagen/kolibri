<template>
  <template v-if="(elementDef instanceof NumberInputDef)">
    <!-- check if default value is filled, and if so, set value.
    For some reason causes errors when check is baked directly
    into the :value binding below (:value="!!elementDef.defaultValue ? element.defaultValue : null"
    should do but causes input fields to be unusable)-->
    <template v-if="elementDef.defaultValue === 0 || !!elementDef.defaultValue">
      <input :id="getValueInputId()"
             class="form-input metric"
             type="number"
             :step=elementDef.step
             :value="elementDef.defaultValue"
             @input="updateValueEvent"
             placeholder="Number Input">
    </template>
    <template v-else>
      <input :id="getValueInputId()"
             class="form-input metric"
             type="number"
             :step=elementDef.step
             @input="updateValueEvent"
             placeholder="Number Input">
    </template>
  </template>
  <template v-if="(elementDef instanceof StringInputDef)">
    <template v-if="!!elementDef.defaultValue">
      <input :id="getValueInputId()"
             class="form-input metric"
             type="text"
             :value="(!!elementDef.defaultValue) ? elementDef.defaultValue : null"
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
  <template v-if="(elementDef instanceof BooleanInputDef)">
    <label class="form-radio form-inline">
      <input :id="getValueInputId()"
             type="radio"
             :name="getValueInputId()"
             :value="true"
             :checked="(elementDef.defaultValue === true) ? '' : null"
             @change="updateValueEvent">
      <i class="form-icon"></i>
      true
    </label>
    <label class="form-radio form-inline">
      <input :id="getValueInputId()"
             type="radio"
             :name="getValueInputId()"
             :value="false"
             :checked="(elementDef.defaultValue === false) ? '' : null"
             @change="updateValueEvent">
      <i class="form-icon"></i>
      false
    </label>
  </template>
  <template v-if="(elementDef instanceof ChoiceInputDef)">
    <template v-for="element in elementDef.choices">
      <label class="form-radio form-inline">
        <input :id="getValueInputId()"
               type="radio"
               :name="getValueInputId()"
               :value="element"
               :checked="(elementDef.defaultValue === element) ? '' : null"
               @change="updateValueEvent">
        <i class="form-icon"></i>
        {{ element }}
      </label>
    </template>
  </template>
  <template v-if="(elementDef instanceof FloatChoiceInputDef)">
    <template v-for="element in elementDef.choices">
      <label class="form-radio form-inline">
        <input :id="getValueInputId()"
               type="radio"
               :name="getValueInputId()"
               :value="element"
               :checked="(elementDef.defaultValue === element) ? '' : null"
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
</template>

<script>
import {onMounted, onUpdated, ref, watch} from "vue";
import {
  InputDef, StringInputDef, BooleanInputDef, NumberInputDef, InputType,
  ChoiceInputDef, FloatChoiceInputDef
} from "../../../utils/dataValidationFunctions.ts"

export default {

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
    },
    resetCounter: {
      type: Number,
      required: false,
      default: 0
    }
  },
  emits: ['valueChanged'],
  components: {},
  methods: {
  },

  setup(props, context) {
    let value = ref(minValueDefault())

    function minValueDefault() {
      return (props.elementDef.validation.min !== undefined) ? props.elementDef.validation.min : 0
    }

    function getValueInputId() {
      return 'k-' + props.elementDef.elementId + "-" + props.name + "-input-" + props.position
    }

    function getToastId() {
      return 'k-' + props.elementDef.elementId + "-" + props.name + '-msg-toast-' + props.position
    }

    function getToastContentId() {
      return 'k-' + props.elementDef.elementId + "-" + props.name + '-msg-toast-content-' + props.position
    }

    function resetValues() {
      props.elementDef.defaultValue = undefined
      value.value = minValueDefault()
    }

    let convertInputToNumber = (props.elementDef instanceof ChoiceInputDef) &&
        (props.elementDef.choices.filter(choice => isNaN(choice)).length == 0)

    let validator = props.elementDef.getInputValidation()

    watch(() => props.elementDef, (newValue, oldValue) => {
      resetValues()
    })

    watch(() => props.resetCounter, (newValue, oldValue) => {
      console.info(`element '${props.name}', resetCounter increase: ${newValue}`)
      if (newValue > oldValue) {
        resetValues()
      }
    })

    onUpdated(() => {
      // note that we need to update the validator here as its possible another elementDef has been passed.
      // if that new def carries another validation, we'd be still using the old, thus the update here.
      // NOTE: do not set possibly changing state in the setup or update it with proper hooks
      validator = props.elementDef.getInputValidation()
    })

    onMounted(() => {
      // if any value passed in props.fillWithValue, we set
      if (props.initWithValue !== undefined) {
        props.elementDef.defaultValue = props.initWithValue
        updateValue(props.initWithValue)
      }
    })

    function parseRightType(val) {
      if (props.elementDef.valueType === InputType.INT) {
        return parseInt(val)
      } else if ([InputType.FLOAT, InputType.FLOAT_CHOICE]
          .includes(props.elementDef.valueType)) {
        return parseFloat(val)
      } else if (props.elementDef.valueType === InputType.BOOLEAN) {
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

    function promoteCurrentStateUp() {
      context.emit('valueChanged', {name: props.name, value: value.value, position: props.position})
    }

    function updateValue(newValue) {
      if (convertInputToNumber) {
        newValue = Number(newValue)
      }
      let validationResult = validator.validate(newValue)
      if (validationResult.isValid) {
        hideModal()
        value.value = parseRightType(newValue)
        // emitting change event to make parent element react to update / update its structure
        promoteCurrentStateUp()
      } else {
        showModalMsg(validationResult.failReason)
      }
    }

    function updateValueEvent(valueEvent) {
      let newValue = valueEvent.target.value
      updateValue(newValue)
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
      value
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



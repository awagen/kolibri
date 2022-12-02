<template>
  <div class="k-input-container">
    <template v-if="(usedElementDef instanceof NumberInputDef)">
      <!-- check if default value is filled, and if so, set value.
      For some reason causes errors when check is baked directly
      into the :value binding below (:value="!!elementDef.defaultValue ? element.defaultValue : null"
      should do but causes input fields to be unusable)-->

      <!-- NOTE that we need to define key below otherwise
       focus will drop after typing of first char and needs another
       click. Setting a static key will avoid this recreation of
       input element and thus keep focus -->
      <template v-if="value === 0 || !!value">
        <input :id="getValueInputId"
               class="form-input metric"
               type="number"
               :step=usedElementDef.step
               :value="value"
               @input="updateValueEvent"
               placeholder="Number Input"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
      </template>
      <template v-else>
        <input :id="getValueInputId"
               class="form-input metric"
               type="number"
               :step=usedElementDef.step
               @input="updateValueEvent"
               placeholder="Number Input"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
      </template>
    </template>
    <template v-if="(usedElementDef instanceof StringInputDef)">
      <template v-if="!!value">
        <input :id="getValueInputId"
               class="form-input metric"
               type="text"
               :value="(!!value) ? value : null"
               @input="updateValueEvent"
               placeholder="Text Input"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
      </template>
      <template v-else>
        <input :id="getValueInputId"
               class="form-input metric"
               type="text"
               @input="updateValueEvent"
               placeholder="Text Input"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
      </template>
    </template>
    <template v-if="(usedElementDef instanceof BooleanInputDef)">
      <label class="form-radio form-inline">
        <input :id="getValueInputId"
               type="radio"
               :name="getValueInputId"
               :value="true"
               :checked="(value === true) ? '' : null"
               @change="updateValueEvent"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
        <i class="form-icon"></i>
        true
      </label>
      <label class="form-radio form-inline">
        <input :id="getValueInputId"
               type="radio"
               :name="getValueInputId"
               :value="false"
               :checked="(value === false) ? '' : null"
               @change="updateValueEvent"
               :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
        >
        <i class="form-icon"></i>
        false
      </label>
    </template>
    <template v-if="(usedElementDef instanceof ChoiceInputDef)">
      <template v-for="element in usedElementDef.choices">
        <label class="form-radio form-inline">
          <input :id="getValueInputId"
                 type="radio"
                 :name="getValueInputId"
                 :value="element"
                 :checked="(value === element) ? '' : null"
                 @change="updateValueEvent"
                 :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
          >
          <i class="form-icon"></i>
          {{ element }}
        </label>
      </template>
    </template>
    <template v-if="(usedElementDef instanceof FloatChoiceInputDef)">
      <template v-for="element in usedElementDef.choices">
        <label class="form-radio form-inline">
          <input :id="getValueInputId"
                 type="radio"
                 :name="getValueInputId"
                 :value="element"
                 :checked="(value === element) ? '' : null"
                 @change="updateValueEvent"
                 :key="position + '-' + name + '-' + JSON.stringify(usedElementDef.toObject())"
          >
          <i class="form-icon"></i>
          {{ element }}
        </label>
      </template>
    </template>
    <!-- Toast element for warnings / validation messages -->
    <div :id="getToastId" class="toast toast-warning display-none">
      <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
      <span :id="getToastContentId"></span>
    </div>
  </div>
</template>

<script>
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
  data() {
    return {
      usedElementDef: this.elementDef.copy(this.elementDef.elementId),
      validator: this.elementDef.getInputValidation(),
      value: this.initWithValue,
      convertInputToNumber: (this.elementDef instanceof ChoiceInputDef) &&
          (this.elementDef.choices.filter(choice => isNaN(choice)).length == 0)
    }
  },
  components: {},
  computed: {
    getValueInputId() {
      return getInputElementId(this.usedElementDef.elementId, this.name, this.position)
    },

    getToastId() {
      return getInputElementToastId(this.usedElementDef.elementId, this.name, this.position)
    },

    getToastContentId() {
      return getInputElementToastContentId(this.usedElementDef.elementId, this.name, this.position)
    },
  },
  methods: {
    parseRightType(val) {
      if (this.usedElementDef.valueType === InputType.INT) {
        return parseInt(val)
      } else if ([InputType.FLOAT, InputType.FLOAT_CHOICE].includes(this.usedElementDef.valueType)) {
        return parseFloat(val)
      } else if (this.usedElementDef.valueType === InputType.BOOLEAN) {
        return (val === null || val === undefined) ? val : (val === 'true' || val === true)
      }
      return val
    },

    emitValueConfirmEvent() {
      this.$emit('valueConfirm', {name: this.name, value: this.value, position: this.position})
    },

    emitValueChangedEvent() {
      this.$emit('valueChanged', {name: this.name, value: this.value, position: this.position})
    },

    updateValue(newValue, isInitValue) {
      if (newValue === undefined && isInitValue) {
        this.emitValueConfirmEvent()
        return;
      }
      if (this.convertInputToNumber) {
        newValue = Number(newValue)
      }
      let validationResult = this.validator.validate(newValue)
      if (validationResult.isValid) {
        this.hideModal()
        this.value = this.parseRightType(newValue)
        // emitting change event to make parent element react to update / update its structure
        if (isInitValue) {
          this.emitValueConfirmEvent()
        } else {
          this.emitValueChangedEvent()
        }
      } else {
        this.value = undefined
        this.showModalMsg(validationResult.failReason)
        this.emitValueChangedEvent()
      }
    },

    /**
     * Function for manual edit
     * @param valueEvent
     */
    updateValueEvent(valueEvent) {
      let newValue = valueEvent.target.value
      this.updateValue(newValue, false)
    },

    showModalMsg(msg) {
      document.getElementById(this.getToastContentId).textContent = msg;
      document.getElementById(this.getToastId).classList.remove("display-none");
    },

    hideModal() {
      document.getElementById(this.getToastContentId).textContent = "";
      document.getElementById(this.getToastId).classList.add("display-none");
    }
  },

  updated() {
    this.convertInputToNumber = (this.usedElementDef instanceof ChoiceInputDef) &&
        (this.usedElementDef.choices.filter(choice => isNaN(choice)).length == 0)
    // note that we need to update the validator here as its possible another elementDef has been passed.
    // if that new def carries another validation, we'd be still using the old, thus the update here.
    // NOTE: do not set possibly changing state in the setup or update it with proper hooks
    this.validator = this.usedElementDef.getInputValidation()
  },

  mounted() {
    if (this.value !== undefined) {
      this.updateValue(this.value, true)
    }

    // document.addEventListener('change', function handle(event) {
    //   if (event.target.id !== this.getValueInputId) {
    //     return
    //   }
    //   let validationResult = this.validator.validate(event.target.value)
    //   if (validationResult.isValid) {
    //     this.hideModal()
    //   }
    // });
  },

  setup() {

    return {
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



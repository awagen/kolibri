<template>

  <!-- adding key wrapper with col-3 and value wrapper with col-9 to ensure correct relative sizes
   when wrapping this in another container -->
  <div :key="childKeyValue" class="col-3 col-sm-12 k-float-left">
    <!-- key input -->
    <!-- NOTE: the key might just be given and not changeable, in which case
     just create a key placeholder with the fixed value -->
    <template v-if="keyValue === undefined">
      <SingleValueStructDef
          @valueChanged="keyValueChanged"
          @valueConfirm="valueConfirm"
          :name="name + '-key-' + position"
          :position="position"
          :element-def="keyInputDef"
          :init-with-value="currentKeyValue"
      >
      </SingleValueStructDef>
    </template>
    <template v-else>
      <!-- since we have a fixed value here,  -->
      <span class="k-fixed-key">{{ keyValue }}</span>
    </template>

  </div>

  <div class="col-9 col-sm-12 k-float-right">

    <!-- value input -->
    <template v-if="(valueInputDef instanceof SingleValueInputDef)">
      <SingleValueStructDef
          @valueChanged="valueChanged"
          @valueConfirm="valueConfirm"
          :name="name + '-value-' + position"
          :position="position"
          :element-def="valueInputDef"
          :init-with-value="currentValueValue"
      >
      </SingleValueStructDef>
    </template>
    <!-- value input -->
    <template v-if="(valueInputDef instanceof SeqInputDef)">
      <GenericSeqStructDef
          @valueChanged="valueChanged"
          :name="name + '-value-' + position"
          :input-def="valueInputDef.inputDef"
          :position="position"
          :init-with-value="currentValueValue"
      >
      </GenericSeqStructDef>
    </template>

  </div>

</template>

<script>
import SingleValueStructDef from "./SingleValueStructDef.vue";
import GenericSeqStructDef from "./GenericSeqStructDef.vue";
import {InputDef, StringInputDef, SingleValueInputDef, SeqInputDef} from "../../../utils/dataValidationFunctions";
import {safeGetArrayValueAtIndex} from "../../../utils/baseDatatypeFunctions";

export default {

  props: {
    name: {
      type: String,
      required: true
    },
    position: {
      type: Number,
      required: true
    },
    // one of keyInputDef or keyValue should be provided. If key value is passed, the
    // assumption is that it is a fixed value, if keyInputDef is passed instead, is assumed
    // that key is flexible but needs to adhere to the passed keyInputDef format / validation
    keyInputDef: {
      type: StringInputDef,
      required: false
    },
    keyValue: {
      type: String,
      required: false
    },
    valueInputDef: {
      type: InputDef,
      required: true
    },
    initWithValue: {
      type: Array,
      required: false,
      default: []
    }
  },
  emits: ['valueChanged', 'valueConfirm'],
  components: {
    SingleValueStructDef,
    GenericSeqStructDef
  },
  data() {
    return {
      currentKeyValue: (this.keyValue !== undefined && this.keyValue !== null) ? this.keyValue : safeGetArrayValueAtIndex(this.initWithValue, 0, undefined),
      currentValueValue: safeGetArrayValueAtIndex(this.initWithValue, 1, undefined),
      childKeyValue: 0
    }
  },
  methods: {

    increaseChildKeyCounter() {
      console.debug(`increasing childKeyValue: ${this.childKeyValue + 1}`)
      this.childKeyValue = this.childKeyValue + 1
    },

    promoteCurrentStateUp() {
      this.$emit("valueChanged", {
        "name": this.currentKeyValue,
        "value": this.currentValueValue,
        "position": this.position
      })
    },

    /**
     * event handler where attributes.value provides the updated value
     * Emits valueChanged event with result of shape {"name": [keyValue], "value": [valueValue]}
     */
    valueChanged(attributes) {
      this.currentValueValue = attributes.value
      this.promoteCurrentStateUp()
    },

    valueConfirm(attributes) {
      this.$emit("valueConfirm", {"name": this.currentKeyValue, "value": this.currentValueValue, "position": this.position})
    },

    /**
     * event handler where attributes.value provides the updated key value.
     * Emits valueChanged event with result of shape {"name": [keyValue], "value": [valueValue]}
     */
     keyValueChanged(attributes) {
      this.currentKeyValue = attributes.value
      this.promoteCurrentStateUp()
    }
  },

  setup(props, context) {

    return {
      SingleValueInputDef,
      SeqInputDef
    }
  }

}

</script>

<style scoped>

.k-float-right {
  float: right;
}

.k-float-left {
  float: left;
}

</style>



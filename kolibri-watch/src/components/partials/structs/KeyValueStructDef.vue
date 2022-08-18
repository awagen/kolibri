<template>

  <!-- adding key wrapper with col-3 and value wrapper with col-9 to ensure correct relative sizes
   when wrapping this in another container -->
  <div class="col-3 col-sm-12 k-float-left">
    <!-- key input -->
    <!-- NOTE: the key might just be given and not changeable, in which case
     just create a key placeholder with the fixed value -->
    <template v-if="keyValue === undefined">
      <SingleValueStructDef
          @value-changed="keyValueChanged"
          :name="name + '-key-' + position"
          :position="position"
          :element-def="keyInputDef">
      </SingleValueStructDef>
    </template>
    <template v-else>
      <!-- since we have a fixed value here,  -->
      <span class="k-fixed-key">{{ keyValue }}</span>
    </template>

  </div>

  <div class="col-9 col-sm-12 k-float-right">

    <!-- value input -->
    <SingleValueStructDef
        @value-changed="valueChanged"
        :name="name + '-value-' + position"
        :position="position"
        :element-def="valueInputDef">
    </SingleValueStructDef>

  </div>

</template>

<script>
import {ref} from "vue";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {InputDef, StringInputDef} from "../../../utils/dataValidationFunctions";

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
    }
  },
  emits: ['valueChanged'],
  components: {
    SingleValueStructDef
  },
  methods: {},
  setup(props, context) {

    let keyValue = ref(props.keyValue)
    let valueValue = ref(undefined)

    /**
     * event handler where attributes.value provides the updated value
     * Emits valueChanged event with result of shape {"name": [keyValue], "value": [valueValue]}
     */
    function valueChanged(attributes) {
      valueValue.value = attributes.value
      context.emit("valueChanged", {"name": keyValue.value, "value": valueValue.value, "position": props.position})
    }

    /**
     * event handler where attributes.value provides the updated key value.
     * Emits valueChanged event with result of shape {"name": [keyValue], "value": [valueValue]}
     */
    function keyValueChanged(attributes) {
      keyValue.value = attributes.value
      context.emit("valueChanged", {"name": keyValue.value, "value": valueValue.value, "position": props.position})
    }

    return {
      valueChanged,
      keyValueChanged
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



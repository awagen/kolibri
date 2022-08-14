<template>
<!-- struct that takes some struct def and allows adding of
elements where all have to adhere to the passed def -->


        <template v-for="(field, index) in addedInputDefs">
          <template v-if="(field instanceof SingleValueInputDef)">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="field">
            </SingleValueStructDef>
          </template>
          <div class="k-form-separator"></div>
        </template>

        <div class="k-add-button-container">
          <button type="button" @click="addNextInputElement()" class="btn btn-action k-add-button s-circle">
            <i class="icon icon-plus"></i>
          </button>
        </div>


</template>

<script>
import {InputDef, SingleValueInputDef} from "../../../utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {onMounted, ref} from "vue";

export default {

  props: {
    name: String,
    inputDef: InputDef
  },

  components: {SingleValueStructDef},
  methods: {

  },
  setup(props, context) {

    let addedInputDefs = ref([])
    console.info("added input defs: ")
    console.log(addedInputDefs.value)
    let addedInputValues = ref([])

    function generateIndexedInputDefForIndex(index) {
      return props.inputDef.copy(`${props.inputDef.name}-index-${index}`,
          `${props.inputDef.elementId}-index-${index}`)
    }

    function generateIndexedInputDef() {
      let newItemIndex = addedInputDefs.value.length
      return generateIndexedInputDefForIndex(newItemIndex)
    }

    function valueChanged(attributes) {
      console.info("value changed event: ")
      console.log(attributes)
      let split = attributes.name.split("-")
      let changedIndex = split[split.length - 1]
      console.info("changed index: " + changedIndex)
      if (addedInputValues.value.length > changedIndex) {
        addedInputValues.value[changedIndex] = attributes.value
      }
      context.emit("valueChanged", {"name": props.name, "value": addedInputValues.value})
      console.info("child value changed: " + attributes.name + "/" + attributes.value)
    }

    /**
     * Add new input def and an empty value
     */
    function addNextInputElement(){
      addedInputValues.value.push(undefined)
      addedInputDefs.value.push(generateIndexedInputDef())
    }

    onMounted(() => {
      addedInputValues.value.push(undefined)
      addedInputDefs.value.push(generateIndexedInputDefForIndex(0))
    })



    return {
      addedInputDefs,
      SingleValueInputDef,
      valueChanged,
      addNextInputElement
    }
  }

}

</script>

<style scoped>

button.k-add-button {
  background-color: transparent;
  border-width: 0;
  color: white;
}

button.k-add-button:hover {
  background-color: #588274;
}

.k-add-button-container {
  text-align: center;
  margin-top: 1em;
}

</style>



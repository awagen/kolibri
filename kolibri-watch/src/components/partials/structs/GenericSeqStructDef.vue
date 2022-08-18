<template>
  <!-- struct that takes some struct def and allows adding of
  elements where all have to adhere to the passed def -->

  <div class="k-seq-container">
    <template v-for="(field, index) in addedInputDefs">
      <template v-if="(field instanceof SingleValueInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + field.elementId">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="field"
                :name="name + '-' + index"
                :position="index">
            </SingleValueStructDef>
          </div>
          <div class="k-delete-button">
            <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>
      <div class="k-form-separator"></div>
    </template>

    <div class="k-add-button-container">
      <button type="button" @click.prevent="addNextInputElement()" class="btn btn-action k-add-button s-circle">
        <i class="icon icon-plus"></i>
      </button>
    </div>

  </div>

</template>

<script>
import {
  InputDef,
  SingleValueInputDef
} from "../../../utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {onMounted, ref} from "vue";

export default {

  props: {
    name: String,
    inputDef: InputDef
  },

  emits: ['valueChanged'],
  components: {SingleValueStructDef},
  methods: {},
  setup(props, context) {

    let addedInputDefs = ref([])
    let addedInputValues = ref([])

    function generateIndexedInputDefForIndex(index) {
      let updatedCopy = props.inputDef.copy(
          `${props.inputDef.elementId}-index-${index}`, addedInputValues.value[index])
      console.debug(`adding updated element copy ${JSON.stringify(updatedCopy.toObject())}`)
      return updatedCopy
    }

    function generateIndexedInputDef() {
      let newItemIndex = addedInputDefs.value.length
      return generateIndexedInputDefForIndex(newItemIndex)
    }

    function valueChanged(attributes) {
      console.debug("value changed event: ")
      console.debug(attributes)
      let changedIndex = attributes.position
      console.debug("changed index: " + changedIndex)
      if (addedInputValues.value.length > changedIndex) {
        addedInputValues.value[changedIndex] = attributes.value
      }
      context.emit("valueChanged", {"name": props.name, "value": addedInputValues.value})
      console.debug("value changed event: ")
      console.debug({"name": props.name, "value": addedInputValues.value})
    }

    /**
     * Add new input def and an empty value
     */
    function addNextInputElement() {
      addedInputValues.value.push(undefined)
      addedInputDefs.value.push(generateIndexedInputDef())
    }

    /**
     * Deletion of input element at given index.
     * As current logic doesnt directly bind what is displayed in the html input but only updates values if they
     * pass validation where the index matches, on deletion of an input element we also have to make sure
     * we adjust all indices in addedInputDefs
     **/
    function deleteInputElement(index) {
      addedInputValues.value.splice(index, 1)
      addedInputDefs.value.splice(index, 1)
      // now adjust indices
      let newInputDefs = []
      for (let [defIndex, _] of addedInputDefs.value.entries()) {
        newInputDefs.push(generateIndexedInputDefForIndex(defIndex))
      }
      addedInputDefs.value = newInputDefs
      // notify parent of change
      context.emit("valueChanged", {"name": props.name, "value": addedInputValues.value})
    }

    onMounted(() => {
      addedInputValues.value.push(undefined)
      addedInputDefs.value.push(generateIndexedInputDefForIndex(0))
    })

    return {
      addedInputDefs,
      SingleValueInputDef,
      valueChanged,
      addNextInputElement,
      deleteInputElement
    }
  }

}

</script>

<style>

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

/** single element edit and delete button **/
.k-single-value-input {
  display: inline-block;
  width: calc(100% - 3em);
  height: 100%;
  overflow: hidden;
}

.k-delete-button {
  float: right;
  display: inline-block;
  width: 2em;
  height: 100%;
}

</style>



<template>
  <!-- struct that takes some struct def and allows adding of
  elements where all have to adhere to the passed def -->

  <div class="k-seq-container">
    <template v-for="(field, index) in addedInputDefs">
      <template v-if="(field instanceof SingleValueInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input" :id="'container-' + field.elementId">
            <SingleValueStructDef
                @value-changed="valueChanged"
                :element-def="field">
            </SingleValueStructDef>
          </div>
          <div class="k-delete-button">
            <a @click="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>
      <div class="k-form-separator"></div>
    </template>

    <div class="k-add-button-container">
      <button type="button" @click="addNextInputElement()" class="btn btn-action k-add-button s-circle">
        <i class="icon icon-plus"></i>
      </button>
    </div>

  </div>

</template>

<script>
import {InputDef, SingleValueInputDef} from "../../../utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {onBeforeUnmount, onMounted, ref} from "vue";

export default {

  props: {
    name: String,
    inputDef: InputDef
  },

  components: {SingleValueStructDef},
  methods: {},
  setup(props, context) {

    let addedInputDefs = ref([])
    let addedInputValues = ref([])
    let observer = undefined

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
      console.info("value changed event: ")
      console.log({"name": props.name, "value": addedInputValues.value})
      // console.info("child value changed: " + attributes.name + "/" + attributes.value)
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

    /**
     * Syncing current state of values with the ordering in the input elements.
     * Only needed on deletions of elements
     */
    function updateInputSequenceValues(){
      // now iterate thru the input elements and set the values as given in addedInputValues
      // TODO: for radio buttons needs setting the right checked
      for (let [valueIndex, value] of addedInputValues.value.entries()) {
        let inputSelector = `#container-${addedInputDefs.value[valueIndex].elementId} input`
        console.debug("trying to find element by selector: " + inputSelector)
        let htmlInputElement = document.querySelector(inputSelector)
        if (htmlInputElement !== null && htmlInputElement !== undefined) {
          console.debug(`found element for index '${valueIndex}', setting value to ${value}`)
          console.debug(htmlInputElement)
          htmlInputElement.value = value
        }
      }
    }

    onMounted(() => {
      addedInputValues.value.push(undefined)
      addedInputDefs.value.push(generateIndexedInputDefForIndex(0))

      // NOTE: this here would not be needed if the values were prefilled depending on the current value state
      // e.g where value is set on rendering. TODO: get rid of this here and rewrite to set the input values
      // initially to the current actual value instead of the current html input value
      observer = new MutationObserver(mutations => {
        let mutationsWithValueNodeRemoval = mutations.filter(mutation => {
          let removedNodes = Array.from(mutation["removedNodes"])
          let removedValueNodes = removedNodes.filter((node) => node.className === 'k-value-and-delete')
          return removedValueNodes.length > 0
        })
        if (mutationsWithValueNodeRemoval !== undefined && mutationsWithValueNodeRemoval.length > 0) {
          console.debug("seen removal of seq entries")
          console.debug(mutationsWithValueNodeRemoval)
          // execute value adjustment logic
          updateInputSequenceValues()
        }
      });
      observer.observe(
          document.querySelector('.k-seq-container'),
          {
            attributes: true, childList: true, characterData: true, subtree: true
          }
      )


    })

    onBeforeUnmount(() => {
      // Clean up
      observer.disconnect();
      observer = undefined
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



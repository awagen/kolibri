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
                :position="index"
                :init-with-value="getInitValueForIndexKey(index)"
                :reset-counter="childrenResetCounter"
            >
            </SingleValueStructDef>
          </div>
          <div class="k-delete-button">
            <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>

      <template v-if="(field instanceof SeqInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + field.elementId">
            <GenericSeqStructDef
                @value-changed="valueChanged"
                :name="name"
                :input-def="field.inputDef"
                :position="index"
                :init-with-value="getInitValueForIndexKey(index)"
                :reset-counter="childrenResetCounter"
            >
            </GenericSeqStructDef>
          </div>
          <div class="k-delete-button">
            <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>

      <template v-if="(field instanceof NestedFieldSequenceInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + field.elementId">
            <NestedFieldSeqStructDef
                @value-changed="valueChanged"
                :conditional-fields="field.conditionalFields"
                :fields="field.fields"
                :name="name"
                :position="index"
                :is-root="false"
                :init-with-value="getInitValueForIndexKey(index)"
                :reset-counter="childrenResetCounter"
            >
            </NestedFieldSeqStructDef>
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
  SingleValueInputDef,
  NestedFieldSequenceInputDef,
  SeqInputDef
} from "@/utils/dataValidationFunctions";
import {onMounted, ref, defineAsyncComponent, watch} from "vue";
import {saveGetArrayValueAtIndex} from "@/utils/baseDatatypeFunctions";

export default {

  props: {
    name: String,
    inputDef: InputDef,
    position: Number,
    description: String,
    initWithValue: {
      type: Array,
      required: false,
      default: []
    },
    resetCounter: {
      type: Number,
      required: false,
      default: 0
    }
  },
  emits: ['valueChanged'],
  components: {
    SingleValueStructDef: defineAsyncComponent(() =>
        import('./SingleValueStructDef.vue')
    ),
    NestedFieldSeqStructDef: defineAsyncComponent(() =>
        import('./NestedFieldSeqStructDef.vue')
    )
  },
  methods: {
  },
  setup(props, context) {

    let addedInputDefs = ref([])
    let addedInputValues = ref([])

    // counter solely set to be passed as props to children such that they can react on changes.
    // A change of this value signals to children that they shall reset their state
    let childrenResetCounter = ref(0)

    /**
     * Increase value passed as props to children such that children can react to changes with
     * a reset of their state
     */
    function increaseChildrenResetCounter() {
      childrenResetCounter.value = childrenResetCounter.value + 1
    }

    /**
     * Reset the currently set values
     */
    function resetValues() {
      addedInputDefs.value = []
      addedInputValues.value = []
    }

    /**
     * The reset counter is to be increased by the parent whenever we need a reset of the data set in this component
     * and its children. Thus we watch for a change here 1) notify the children of this component by increasing the
     * childrenResetCounter, 2) resetting values of this component and 3) promote the resulting state back up to the
     * parent
     */
    watch(() => props.resetCounter, (newValue, oldValue) => {
      console.debug(`element '${props.name}', resetCounter increase: ${newValue}`)
      if (newValue > oldValue) {
        increaseChildrenResetCounter()
        resetValues()
        promoteCurrentStateUp()
      }
    })

    /**
     * Get initialization value for the passed index
     * @param index
     * @returns If any initialization value for the passed index is set, return that value, otherwise returns undefined
     */
    function getInitValueForIndexKey(index) {
      return saveGetArrayValueAtIndex(props.initWithValue, index, undefined)
    }

    /**
     * Generate copy of the inputDef for passed index
     * @param index
     * @returns Correctly indexed copy for the passed inputDef
     */
    function generateIndexedInputDefForIndex(index) {
      let updatedCopy = props.inputDef.copy(
          `${props.inputDef.elementId}-index-${index}`, addedInputValues.value[index])
      return updatedCopy
    }

    /**
     * Creates a copy of the inputDef with properly set indexed (e.g increasing last used index by one)
     * @returns Correctly indexed copy for the passed inputDef
     */
    function generateIndexedInputDef() {
      let newItemIndex = addedInputDefs.value.length
      return generateIndexedInputDefForIndex(newItemIndex)
    }

    /**
     * Communicate change of value to parent
     */
    function promoteCurrentStateUp() {
      context.emit("valueChanged", {
        "name": props.name,
        "value": addedInputValues.value,
        "position": props.position
      })
    }

    /**
     * Handles value change
     * @param attributes
     */
    function valueChanged(attributes) {
      let changedIndex = attributes.position
      if (addedInputValues.value.length > changedIndex) {
        addedInputValues.value[changedIndex] = attributes.value
      }
      promoteCurrentStateUp()
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
     * As current logic doesn't directly bind what is displayed in the html input but only updates values if they
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

    // initially we either initialize all slots needed for the initialization values
    // or if not set we initialize the initial element
    onMounted(() => {
      if (props.initWithValue !== undefined && props.initWithValue.length > 0) {
        props.initWithValue.forEach(_ => {
          addNextInputElement()
        })
      }
      else {
        addNextInputElement()
      }
    })

    return {
      addedInputDefs,
      SingleValueInputDef,
      NestedFieldSequenceInputDef,
      SeqInputDef,
      valueChanged,
      addNextInputElement,
      deleteInputElement,
      getInitValueForIndexKey,
      childrenResetCounter
    }
  }

}

</script>

<style>

button.k-add-button {
  background-color: #588274;
  border-width: 0;
  color: white;
}

button.k-add-button:hover {
  background-color: #00ED00;
}

.k-add-button-container {
  text-align: center;
  margin-top: 1em;
  margin-bottom: 1em;
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



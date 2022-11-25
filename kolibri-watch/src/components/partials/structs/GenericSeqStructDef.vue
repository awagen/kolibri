<template>
  <!-- struct that takes some struct def and allows adding of
  elements where all have to adhere to the passed def -->

  <div class="k-seq-container">
    <template :key="childKeyValue + '-' + item.elementId" v-for="(item, index) in addedInputDefs">

      <template v-if="(item instanceof SingleValueInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + item.elementId">
            <SingleValueStructDef
                @valueChanged="valueChanged"
                @valueConfirm="valueConfirm"
                :element-def="item"
                :name="name + '-' + index"
                :position="index"
                :init-with-value="getInitValueForIndexKey(index)"
            >
            </SingleValueStructDef>
          </div>
          <div class="k-delete-button">
            <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>

      <template v-if="(item instanceof SeqInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + item.elementId">
            <GenericSeqStructDef
                @value-changed="valueChanged"
                :name="name"
                :input-def="item.inputDef"
                :position="index"
                :init-with-value="getInitValueForIndexKey(index)"
            >
            </GenericSeqStructDef>
          </div>
          <div class="k-delete-button">
            <a @click.prevent="deleteInputElement(index)" href="#" class="k-delete btn btn-clear"
               aria-label="Close" role="button"></a>
          </div>
        </div>
      </template>

      <template v-if="(item instanceof NestedFieldSequenceInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + item.elementId">
            <NestedFieldSeqStructDef
                @value-changed="valueChanged"
                :conditional-fields="item.conditionalFields"
                :fields="item.fields"
                :name="name"
                :position="index"
                :is-root="false"
                :init-with-value="getInitValueForIndexKey(index)"
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
} from "../../../utils/dataValidationFunctions.ts";
import {defineAsyncComponent, ref} from "vue";
import _ from "lodash";
import {safeGetArrayValueAtIndex} from "../../../utils/baseDatatypeFunctions.ts";


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
  data() {
    return {
      addedInputDefs: this.initIndexedInputDefsForInputValues(this.initWithValue),
      addedInputValues: _.cloneDeep((this.initWithValue !== undefined && this.initWithValue !== null) ? this.initWithValue : []),
    }
  },
  computed: {
    inputValueSize() {
      return this.addedInputValues.length
    },
    inputDefSize() {
      return this.addedInputDefs.length
    }
  },
  methods: {
    /**
     * Get initialization value for the passed index
     * @param idx
     * @returns If any initialization value for the passed index is set, return that value, otherwise returns undefined
     */
    getInitValueForIndexKey(idx) {
      return safeGetArrayValueAtIndex(this.addedInputValues, idx, undefined)
    },

    /**
     * Add new input def and an empty value
     */
    addNextInputElement(value = undefined) {
      this.addedInputValues.push(value)
      this.addedInputDefs.push(this.generateIndexedInputDef())
    },

    valueConfirm(attributes) {
      this.promoteCurrentStateUp()
    },

    /**
     * Handles value change
     * @param attributes
     */
    valueChanged(attributes) {
      let changedIndex = attributes.position
      if (this.inputValueSize > changedIndex) {
        this.addedInputValues[changedIndex] = attributes.value
      }
      this.promoteCurrentStateUp()
    },

    /**
     * Communicate change of value to parent
     */
    promoteCurrentStateUp() {
      this.$emit("valueChanged", {
        "name": this.name,
        "value": this.addedInputValues,
        "position": this.position
      })
    },

    /**
     * Deletion of input element at given index.
     * As current logic doesn't directly bind what is displayed in the html input but only updates values if they
     * pass validation where the index matches, on deletion of an input element we also have to make sure
     * we adjust all indices in addedInputDefs
     **/
    deleteInputElement(index) {
      this.addedInputValues.splice(index, 1)
      this.addedInputDefs.splice(index, 1)
      // notify parent of change
      this.$emit("valueChanged", {"name": this.name, "value": this.addedInputValues})
      this.increaseChildKeyValueIndex()
    },

    initIndexedInputDefsForInputValues(values) {
      if (values === undefined || values === null) {
        return []
      }
      let defArray = values.map((value, index) => {
        return this.generateIndexedInputDefForIndex(index, values)
      })
      return defArray
    },

    /**
     * Generate copy of the inputDef for passed index
     * @param index
     * @param values
     * @returns Correctly indexed copy for the passed inputDef
     */
    generateIndexedInputDefForIndex(index, values) {
      let newDefaultValue = values[index]
      return this.inputDef.copy(
          `${this.inputDef.elementId}-index-${index}`,
          newDefaultValue)
    },

    /**
     * Creates a copy of the inputDef with properly set indexed (e.g increasing last used index by one)
     * @returns Correctly indexed copy for the passed inputDef
     */
    generateIndexedInputDef() {
      let newItemIndex = this.inputDefSize
      return this.generateIndexedInputDefForIndex(newItemIndex, this.addedInputValues)
    }
  },

  setup(props, context) {

    let childKeyValueIndex = 0
    let childKeyValue = ref(`${props.name}-${props.position}-${childKeyValueIndex}`)

    function increaseChildKeyValueIndex() {
      childKeyValueIndex += 1
      childKeyValue.value = `${props.name}-${props.position}-${childKeyValueIndex}`
    }

    return {
      SingleValueInputDef,
      NestedFieldSequenceInputDef,
      SeqInputDef,
      childKeyValue,
      increaseChildKeyValueIndex
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



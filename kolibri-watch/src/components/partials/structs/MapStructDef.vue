<template>

  <div class="k-map-name col-3 col-sm-12">
    <DescriptionPopover :description="description"/>
    <span>{{ name }}</span>
  </div>

  <div class="k-seq-container col-9 col-sm-12">

    <!-- NOTE: key value needs to be increased on deletion of element
     to rerender the correct order of elements, otherwise
     you might see first element deleted but last one removed
     in the display -->
    <template :key="childKeyValue" v-for="(field, index) in addedKeyValueDefs">
      <template v-if="(field instanceof KeyValueInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + field.elementId">
            <KeyValueStructDef
                @valueChanged="valueChanged"
                @valueConfirm="valueConfirm"
                :name="name + '-' + index"
                :position="index"
                :key-input-def="field.keyFormat"
                :value-input-def="field.valueFormat"
                :init-with-value="[getKeyForInitIndex(index), getValueForInitIndex(index)]"
                :key="name + '-' + index"
            >
            </KeyValueStructDef>
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
import {KeyValueInputDef} from "../../../utils/dataValidationFunctions.ts";
import KeyValueStructDef from "./KeyValueStructDef.vue";
import DescriptionPopover from "./elements/DescriptionPopover.vue";
import {safeGetArrayValueAtIndex} from "../../../utils/baseDatatypeFunctions";
import {preservingJsonFormat} from "../../../utils/formatFunctions";

export default {

  props: {
    name: {
      type: String,
      required: true
    },
    keyValueInputDef: {
      type: KeyValueInputDef,
      required: true
    },
    description: {
      type: String,
      required: false
    },
    // here init value needs to be an object. To initialize the values, in onMounted enough fields will be initialized
    // and then above the init values are passed in sequence given by extracting a sequence of keys
    // via Object.keys()
    initWithValue: {
      type: Object,
      required: false,
      default: {}
    }
  },
  components: {KeyValueStructDef, DescriptionPopover},
  emits: ["valueChanged"],
  computed: {

    /**
     * From the currently set values generates an Object with the right map key and within it sequentially the
     * key-value pairs created.
     **/
    mapFromKeyValuePairs() {
      let result = {}
      this.addedKeyValuePairs.forEach((keyValuePair) => {
        if (keyValuePair[0] !== undefined) {
          result[keyValuePair[0]] = keyValuePair[1]
        }
      })
      return result
    }

  },

  methods: {

    increaseChildKeyCounter() {
      console.debug(`increasing childKeyValue: ${this.childKeyValue + 1}`)
      this.childKeyValue = this.childKeyValue + 1
    },

    promoteCurrentStateUp() {
      this.$emit("valueChanged", {"name": this.name, "value": this.mapFromKeyValuePairs})
    },

    initializeValues() {
      this.addedKeyValueDefs = []
      this.addedKeyValuePairs = []
      // here we simply initially add enough elements to fit all key-value pairs of initWithValue
      // the actual setting of the right key-value pairs will be done above
      if (this.initWithValue !== undefined && this.initWithValue !== null) {
        for (const [key, value] of Object.entries(this.initWithValue)) {
          this.addedKeyValuePairs.push([key, value])
          this.addedKeyValueDefs.push(this.generateIndexedInputDef())
        }
      } else {
        this.addedKeyValuePairs.push([undefined, undefined])
        this.addedKeyValueDefs.push(this.generateIndexedInputDefForIndex(0))
      }
    },

    /**
     * If any initialisation key-value pairs are passed, determine the n-th key value
     * @param index - index of the key. Order derived by transformation keys from dict to list via Object.keys()
     * @returns string - key value for key at position given by passed index. undefined if out of bounds
     */
    getKeyForInitIndex(index) {
      let valueKeys = this.addedKeyValuePairs.map(x => x[0])
      return safeGetArrayValueAtIndex(valueKeys, index, "")
    },

    /**
     * If any initialisation key-value pairs are passed, determine the n-th value
     * @param index - index of the key. Order derived by transformation keys from dict to list via Object.keys()
     * @returns string - value for key at position given by passed index. undefined if out of bounds
     */
    getValueForInitIndex(index) {
      let valueArray = this.addedKeyValuePairs.map(x => x[1])
      return safeGetArrayValueAtIndex(valueArray, index, "")
    },

    /**
     * Generates a new element out of the given KeyValueInputDef, setting the properly indexed
     * elementId and sets default value to the value in addedKeyValuePairs for that specific index
     * to make sure we dont loose display of already added values in case we delete an element.
     * This will be undefined if the value corresponds to a new index for which no value yet exists, in which
     * case the undefined default value is ignored within the InputDef object itself
     **/
    generateIndexedInputDefForIndex(index) {
      return this.keyValueInputDef.copy(
          `${this.keyValueInputDef.elementId}-index-${index}`, this.addedKeyValuePairs[index])
    },

    /**
     * Checks how many field definitions were added already and adds the next.
     **/
    generateIndexedInputDef() {
      let newItemIndex = this.addedKeyValueDefs.length
      return this.generateIndexedInputDefForIndex(newItemIndex)
    },

    valueConfirm(attributes) {
      this.promoteCurrentStateUp()
    },

    /**
     * Event on value change. Assumes the 'name' attribute to provide the key of the changed / added entry,
     * 'position' the position in the current key / value sequence and 'value' to give the value of the key/value pair.
     *
     * After the set values are updated, events a state update for its parent to pick up.
     **/
    valueChanged(attributes) {
      let changedIndex = attributes.position
      if (this.addedKeyValuePairs.length > changedIndex) {
        this.addedKeyValuePairs[changedIndex] = [attributes.name, attributes.value]
      }
      this.promoteCurrentStateUp()
    },

    /**
     * Add new input def and an empty value
     */
    addNextInputElement() {
      this.addedKeyValuePairs.push([undefined, undefined])
      this.addedKeyValueDefs.push(this.generateIndexedInputDef())
    },

    /**
     * Deletion of input element at given index.
     * As current logic doesn't directly bind what is displayed in the html input but only updates values if they
     * pass validation where the index matches, on deletion of an input element we also have to make sure
     * we adjust all indices in addedInputDefs
     **/
    deleteInputElement(index) {
      this.addedKeyValuePairs.splice(index, 1)
      // now adjust indices
      let newInputDefs = []
      for (let [defIndex, _] of this.addedKeyValuePairs.entries()) {
        newInputDefs.push(this.generateIndexedInputDefForIndex(defIndex))
      }
      this.addedKeyValueDefs = newInputDefs
      // notify parent of change
      this.promoteCurrentStateUp()
      this.increaseChildKeyCounter()
    }


  },
  data() {
    return {
      // contains the KeyValueInputDefs representing the elements already added
      addedKeyValueDefs: [],
      // contains the values corresponding to the input defs in addedKeyValueDefs
      addedKeyValuePairs: [],
      // key value for child rendering. If changed, recreates the respective visual
      childKeyValue: 0
    }
  },

  mounted() {
    this.initializeValues()
  },

  setup() {
    return {
      KeyValueStructDef,
      KeyValueInputDef
    }
  }

}

</script>

<style scoped>

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

.k-delete-button {
  float: right;
  display: inline-block;
  width: 2em;
  height: 100%;
}

.k-value-and-delete {
  text-align: left;
}

/** single element edit and delete button **/
.k-single-value-input {
  display: inline-block;
  width: calc(100% - 3em);
  height: 100%;
  overflow: hidden;
}

.k-form-separator {
  height: .5em;
}

</style>



<template>

  <!-- TODO: element deletion doesnt yet work properly, yet adding and editing
   does -->
  <div class="k-map-name col-3 col-sm-12">
    <span>{{name}}</span>
  </div>

  <div class="k-seq-container col-9 col-sm-12">

    <template v-for="(field, index) in addedKeyValueDefs">
      <template v-if="(field instanceof KeyValueInputDef)">
        <div class="k-value-and-delete">
          <div class="k-single-value-input"
               :id="'container-' + field.elementId">
            <KeyValueStructDef
                @value-changed="valueChanged"
                :name="name + '-' + index"
                :position="index"
                :key-input-def="field.keyFormat"
                :value-input-def="field.valueFormat">
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
import {onMounted, ref} from "vue";
import {KeyValueInputDef} from "../../../utils/dataValidationFunctions.ts";
import KeyValueStructDef from "./KeyValueStructDef.vue";

export default {

  props: {
    name: {
      type: String,
      required: true
    },
    keyValueInputDef: {
      type: KeyValueInputDef,
      required: true
    }
  },
  components: {KeyValueStructDef},
  emits: ["valueChanged"],
  methods: {},
  setup(props, context) {

    // contains the KeyValueInputDefs representing the elements already added
    let addedKeyValueDefs = ref([])
    // contains the values corresponding to the input defs in addedKeyValueDefs
    let addedKeyValuePairs = ref([])

    // TODO: taking default values into account on value setting on element deletion
    // does not fully work yet
    function generateIndexedInputDefForIndex(index) {
      return props.keyValueInputDef.copy(
          `${props.keyValueInputDef.elementId}-index-${index}`, addedKeyValuePairs.value[index])
    }

    function generateIndexedInputDef() {
      let newItemIndex = addedKeyValueDefs.value.length
      return generateIndexedInputDefForIndex(newItemIndex)
    }

    function valueChanged(attributes) {
      console.info("value changed event: ")
      console.log(attributes)
      let changedIndex = attributes.position
      console.info("changed index: " + changedIndex)
      if (addedKeyValuePairs.value.length > changedIndex) {
        addedKeyValuePairs.value[changedIndex] = [attributes.name, attributes.value]
      }
      context.emit("valueChanged", {"name": props.name, "value": mapFromKeyValuePairs()})
      console.info("value changed event: ")
      console.log({"name": props.name, "value": mapFromKeyValuePairs()})
    }

    /**
     * Add new input def and an empty value
     */
    function addNextInputElement() {
      addedKeyValuePairs.value.push([undefined, undefined])
      addedKeyValueDefs.value.push(generateIndexedInputDef())
    }

    function mapFromKeyValuePairs() {
      let result = {}
      addedKeyValuePairs.value.forEach((keyValuePair) => {
        if (keyValuePair[0] !== undefined) {
          result[keyValuePair[0]] = keyValuePair[1]
        }
      })
      return result
    }

    /**
     * Deletion of input element at given index.
     * As current logic doesnt directly bind what is displayed in the html input but only updates values if they
     * pass validation where the index matches, on deletion of an input element we also have to make sure
     * we adjust all indices in addedInputDefs
     **/
    function deleteInputElement(index) {
      addedKeyValuePairs.value.splice(index, 1)
      addedKeyValueDefs.value.splice(index, 1)
      // now adjust indices
      let newInputDefs = []
      for (let [defIndex, _] of addedKeyValueDefs.value.entries()) {
        newInputDefs.push(generateIndexedInputDefForIndex(defIndex))
      }
      addedKeyValueDefs.value = newInputDefs
      // notify parent of change
      context.emit("valueChanged", {"name": props.name, "value": mapFromKeyValuePairs})
    }

    onMounted(() => {
      addedKeyValuePairs.value.push([undefined, undefined])
      addedKeyValueDefs.value.push(generateIndexedInputDefForIndex(0))
    })

    return {
      addedKeyValueDefs,
      valueChanged,
      deleteInputElement,
      addNextInputElement,
      KeyValueInputDef
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



<template>

  <div class="k-map-name col-3 col-sm-12">
    <DescriptionPopover :description="description"/>
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
import DescriptionPopover from "./elements/DescriptionPopover.vue";

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
    }
  },
  components: {KeyValueStructDef, DescriptionPopover},
  emits: ["valueChanged"],
  methods: {},
  setup(props, context) {

    // contains the KeyValueInputDefs representing the elements already added
    let addedKeyValueDefs = ref([])
    // contains the values corresponding to the input defs in addedKeyValueDefs
    let addedKeyValuePairs = ref([])

    /**
     * Generates a new element out of the given KeyValueInputDef, setting the properly indexed
     * elementId and sets default value to the value in addedKeyValuePairs for that specific index
     * to make sure we dont loose display of already added values in case we delete an element.
     * This will be undefined if the value corresponds to a new index for which no value yet exists, in which
     * case the undefined default value is ignored within the InputDef object itself
     **/
    function generateIndexedInputDefForIndex(index) {
      return props.keyValueInputDef.copy(
          `${props.keyValueInputDef.elementId}-index-${index}`, addedKeyValuePairs.value[index])
    }

    /**
     * Checks how many field definitions were added already and adds the next.
     **/
    function generateIndexedInputDef() {
      let newItemIndex = addedKeyValueDefs.value.length
      return generateIndexedInputDefForIndex(newItemIndex)
    }

    /**
     * Event on value change. Assumes the 'name' attribute to provide the key of the changed / added entry,
     * 'position' the position in the current key / value sequence and 'value' to give the value of the key/value pair.
     *
     * After the set values are updated, events a state update for its parent to pick up.
     **/
    function valueChanged(attributes) {
      console.debug("value changed event: ")
      console.debug(attributes)
      let changedIndex = attributes.position
      console.debug("changed index: " + changedIndex)
      if (addedKeyValuePairs.value.length > changedIndex) {
        addedKeyValuePairs.value[changedIndex] = [attributes.name, attributes.value]
      }
      context.emit("valueChanged", {"name": props.name, "value": mapFromKeyValuePairs()})
      console.debug("value changed event: ")
      console.debug({"name": props.name, "value": mapFromKeyValuePairs()})
    }

    /**
     * Add new input def and an empty value
     */
    function addNextInputElement() {
      addedKeyValuePairs.value.push([undefined, undefined])
      addedKeyValueDefs.value.push(generateIndexedInputDef())
    }

    /**
     * From the currently set values generates an Object with the right map key and within it sequentially the
     * key-value pairs created.
     **/
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
      console.debug("setting addedKeyValuePairs: " + JSON.stringify({"data": addedKeyValuePairs.value}))
      // now adjust indices
      let newInputDefs = []
      for (let [defIndex, _] of addedKeyValuePairs.value.entries()) {
        newInputDefs.push(generateIndexedInputDefForIndex(defIndex))
      }
      console.debug("setting addedKeyValueDefs: " + newInputDefs.map(x => JSON.stringify(x.toObject())))
      addedKeyValueDefs.value = newInputDefs
      // notify parent of change
      context.emit("valueChanged", {"name": props.name, "value": mapFromKeyValuePairs()})
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



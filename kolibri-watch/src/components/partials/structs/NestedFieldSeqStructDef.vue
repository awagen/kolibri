<template>

  <template v-for="(field, index) in [...fields, ...selectedConditionalFields]">
    <div class="form-group k-input-group">

      <template v-if="(field.valueFormat instanceof SingleValueInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.description"/>
          <label class="form-label k-field-name"
                 :for="field.valueFormat.elementId + '-' + field.name + '-' + position + '-' + index">{{
              field.name
            }}</label>
        </div>

        <div :id="field.valueFormat.elementId + '-' + field.name + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <SingleValueStructDef
              @value-changed="valueChanged"
              :name="field.name"
              :element-def="field.valueFormat"
              :position="position * 100 + index"
              :description="field.description"
              :init-with-value="saveGetMapValueForKey(initWithValue, field.name, '')"
              :reset-counter="childrenResetCounter"
          >
          </SingleValueStructDef>
        </div>
      </template>

      <template v-if="(field.valueFormat instanceof SeqInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.description"/>
          <label class="form-label k-field-name"
                 :for="field.valueFormat.elementId + '-' + field.name + '-' + position + '-' + index">{{
              field.name
            }}</label>
        </div>
        <div :id="field.valueFormat.elementId + '-' + field.name + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <GenericSeqStructDef
              @value-changed="valueChanged"
              :name="field.name"
              :input-def="field.valueFormat.inputDef"
              :position="position * 100 + index"
              :description="field.description"
              :init-with-value="saveGetMapValueForKey(initWithValue, field.name, [])"
              :reset-counter="childrenResetCounter"
          >
          </GenericSeqStructDef>
        </div>
      </template>

      <template v-if="(field.valueFormat instanceof MapInputDef)">
        <MapStructDef
            @value-changed="valueChanged"
            :name="field.name"
            :key-value-input-def="field.valueFormat.keyValueDef"
            :description="field.description"
            :init-with-value="saveGetMapValueForKey(initWithValue, field.name, {})"
            :reset-counter="childrenResetCounter"
        >
        </MapStructDef>
      </template>

      <template v-if="(field.valueFormat instanceof NestedFieldSequenceInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.description"/>
          <label class="form-label k-field-name"
                 :for="field.valueFormat.elementId + '-' + field.name + '-' + position + '-' + index">{{
              field.name
            }}</label>
        </div>
        <div :id="field.valueFormat.elementId + '-' + field.name  + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <!-- note: we use v-model as shorthand for providing update method
           which we will use in the counter increase event emit to the component
           itself -->
          <NestedFieldSeqStructDef
              @value-changed="valueChanged"
              :conditional-fields="field.valueFormat.conditionalFields"
              :fields="field.valueFormat.fields"
              :name="field.name"
              :is-root="false"
              :description="field.description"
              :position="position * 100 + index"
              :init-with-value="saveGetMapValueForKey(initWithValue, field.name, {})"
              :reset-counter="childrenResetCounter"
          >
          </NestedFieldSeqStructDef>
        </div>
      </template>

      <div class="k-form-separator"></div>
    </div>
  </template>

</template>

<script>
import {
  SingleValueInputDef,
  SeqInputDef,
  KeyValuePairInputDef,
  KeyValueInputDef,
  MapInputDef,
  NestedFieldSequenceInputDef
} from "@/utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {ref, watch} from "vue";
import {useStore} from 'vuex';
import {saveGetMapValueForKey} from "../../../utils/baseDatatypeFunctions.ts";
import GenericSeqStructDef from "./GenericSeqStructDef.vue";
import KeyValueStructDef from "./KeyValueStructDef.vue";
import MapStructDef from "./MapStructDef.vue";
import DescriptionPopover from "./elements/DescriptionPopover.vue";

export default {

  props: {
    // NOTE: more specific typing of array type doesn't seem to
    // work also when using PropType. Elements here must be of type FieldDef, e.g carrying a name and
    // a InputDef for the input needed
    fields: {type: Array, required: true},
    // conditional fields. This means that the overall composition of FieldDef to display
    // depends on the settings in (permanent / unconditional) fields. Also, conditionFields defined in the conditionalFields need to refer to a field
    // in fields, e.g a permanent fields. Right now we won't implement the dependencies between different conditional fields
    conditionalFields: {type: Array, required: true},
    name: {type: String, required: false},
    isRoot: {type: Boolean, required: true, default: true},
    position: {type: Number, required: false, default: 0},
    description: {type: String, required: false},
    initWithValue: {
      type: Object,
      required: false,
      default: {}
    },
    resetCounter: {
      type: Number,
      required: false,
      default: 0
    }
  },
  emits: ['valueChanged'],
  components: {
    DescriptionPopover,
    GenericSeqStructDef,
    SingleValueStructDef,
    KeyValueStructDef,
    MapStructDef
  },
  computed: {},
  methods: {
  },
  setup(props, context) {
    const store = useStore()
    // values for the permanent fields that are unconditional on other fields
    let fieldStates = ref({})
    // keeping the currently selected conditionalFields in separate state from permanent fields
    let selectedConditionalFields = ref([])
    let selectedConditionalFieldsStates = ref({})
    props.fields.forEach(field => {
      fieldStates.value[field.name] = undefined
    })

    // this for resetting components (but will not call their initial setup again)
    let childrenResetCounter = ref(0)

    function increaseChildrenResetCounter() {
      childrenResetCounter.value = childrenResetCounter.value + 1
    }

    function resetValues() {
      fieldStates.value = {}
      selectedConditionalFields.value = []
      selectedConditionalFieldsStates.value = []
      props.fields.forEach(field => {
        fieldStates.value[field.name] = undefined
      })
    }

    watch(() => props.resetCounter, (newValue, oldValue) => {
      console.info(`element '${props.name}', resetCounter increase: ${newValue}`)
      if (newValue > oldValue && newValue) {
        increaseChildrenResetCounter();
        resetValues();
        promoteCurrentStateUp();
      }
    })

    watch(() => props.fields, (newValue, oldValue) => {
      increaseChildrenResetCounter();
      resetValues();
      promoteCurrentStateUp();
    })

    watch(() => props.conditionalFields, (newValue, oldValue) => {
      increaseChildrenResetCounter();
      resetValues();
      promoteCurrentStateUp();
    })

    /**
     * Given a name and value of a condition field, find those conditional fields that
     * a) depend on the passed field name and b) there exists a mapping for the value of the condition field that
     * contains this field
     * @param conditionFieldName - name of the field to search for dependent conditional fields
     * @param conditionFieldValue - value of the field to search for dependent conditional fields
     * @returns Array of FieldDef objects that are valid for the passed name and value of the conditionField
     */
    function getValidConditionalFieldsForConditionField(conditionFieldName, conditionFieldValue) {
      return props.conditionalFields
          .filter(condField => condField.conditionField === conditionFieldName)
          .filter(condField => condField.mapping.get(conditionFieldValue) !== undefined)
          .flatMap(condField => condField.mapping.get(conditionFieldValue))
          .map(field => field.copy())
    }

    function isConditionalValueAffected(conditionFieldName) {
      return props.conditionalFields
          .find(condField => condField.conditionField === conditionFieldName) !== undefined
    }

    /**
     * Iterates over all key value pairs of non-conditional fields (those are the only ones that can cause
     * an conditional field to become activated) and check which conditional fields would need to be made active
     * (e.g since they are contained in the mapping of the condition
     * @returns {*[]}
     */
    function getAllValidConditionalFields() {
      let validConditionalFields = []
      for (const [key, value] of Object.entries(fieldStates.value)) {
        getValidConditionalFieldsForConditionField(key, value).forEach(element => {
          if (!(element in validConditionalFields)) {
            validConditionalFields.push(element)
          }
        })
      }
      return validConditionalFields
    }


    /**
     * check whether we have any conditional fields that carry the attribute.name as conditionField
     * and contain the current value of the conditionField as key in the mapping. If yes, add all FieldDef that
     * correspond to the current value if not already there.
     * NOTE: need to cherish default values update within the fields objects when a value is updated so
     * that we retain the values if an element is not deleted (e.g. when one or more elements are deleted)
     * @param attributes
     */
    function updateConditionalFields(attributes) {
      // check if the changed field value actually influences any conditional fields
      let conditionalFieldIsAffected = isConditionalValueAffected(attributes.name)
      if (!conditionalFieldIsAffected) {
        return
      }

      // extract all conditional fields that are valid as per the current settings of the non-conditional fields
      let validConditionalFields = getAllValidConditionalFields()
      // extract all conditional fields which are affected by the current value change
      let affectedConditionalFields = getValidConditionalFieldsForConditionField(attributes.name, attributes.value)
      // transform to name to fieldDef map
      let affectedConditionalFieldsByName = {}
      affectedConditionalFields.forEach(field => {
        affectedConditionalFieldsByName[field.name] = field
      })
      // now replace all definitions for the selected fields affected by the condition field update
      // with the currently valid definitions
      for (const [index, selectedField] of selectedConditionalFields.value.entries()) {
        if (affectedConditionalFieldsByName.hasOwnProperty(selectedField.name)) {
          let refreshValue = affectedConditionalFieldsByName[selectedField.name]
          selectedConditionalFields.value[index] = refreshValue.copy(refreshValue.name, true)
          // setting the set value for the field to undefined
          selectedConditionalFieldsStates.value[selectedField.name] = undefined
        }
      }

      let validConditionalFieldNames = validConditionalFields.map(field => field.name)

      let currentlySelectedFieldNames = selectedConditionalFields.value.map(field => field.name)
      let retainFieldNames = currentlySelectedFieldNames.filter(value => validConditionalFieldNames.includes(value))
      let addNewFieldNames = validConditionalFieldNames.filter(value => !currentlySelectedFieldNames.includes(value))
      let addConditionalFields = validConditionalFields.filter(field => addNewFieldNames.includes(field.name))

      // delete those selected conditional fields that don't need to be retained
      selectedConditionalFields.value = selectedConditionalFields.value.filter(field => retainFieldNames.includes(field.name))
      // add those validConditionalFields that need to be added
      selectedConditionalFields.value = selectedConditionalFields.value.concat(addConditionalFields)
      // adjust selectedConditionalFieldStates by removing those keys not in retainFieldNames
      for (let key of Object.keys(selectedConditionalFieldsStates.value)) {
        if (!retainFieldNames.includes(key)) {
          delete selectedConditionalFieldsStates.value[key];
        }
      }
      // prefill those values which are not yet in the selectedConditionalFieldStates but need adding
      addNewFieldNames.forEach(fieldName => selectedConditionalFieldsStates.value[fieldName] = undefined)
    }

    function isUnconditionalField(attributes) {
      return props.fields.map(field => field.name).includes(attributes.name)
    }

    function isSelectedConditionalField(attributes) {
      return selectedConditionalFields.value.map(field => field.name).includes(attributes.name)
    }

    function promoteCurrentStateUp() {
      // if it's a root element,
      // update the global state of this object (merging the unconditional and the conditional fields)
      // otherwise just emit the state update to the parent
      let combinedValue = Object.assign({}, fieldStates.value, selectedConditionalFieldsStates.value)
      if (props.isRoot) {
        let nestedInputDef = new NestedFieldSequenceInputDef(
            "root",
            props.fields,
            props.conditionalFields
        )
        store.commit("updateCurrentJobDefState", {
          jobDefStateObj: combinedValue,
          jobDefState: nestedInputDef
        })
      } else {
        context.emit('valueChanged', {
          name: props.name,
          value: combinedValue,
          position: props.position
        })
      }
    }

    // the state keeping within the single components should take care of set values if state is altered,
    // otherwise we might need to set default values on the field's InputDefs (conditional or unconditional inputs)
    function valueChanged(attributes) {
      let unconditionalField = isUnconditionalField(attributes)
      if (unconditionalField) {
        fieldStates.value[attributes.name] = attributes.value
      } else if (isSelectedConditionalField(attributes)) {
        selectedConditionalFieldsStates.value[attributes.name] = attributes.value
      }
      // update the conditional fields
      if (unconditionalField) {
        updateConditionalFields(attributes)
      }
      promoteCurrentStateUp()
    }

    return {
      SingleValueInputDef,
      valueChanged,
      SeqInputDef,
      NestedFieldSequenceInputDef,
      KeyValuePairInputDef,
      KeyValueInputDef,
      MapInputDef,
      selectedConditionalFields,
      saveGetMapValueForKey,
      childrenResetCounter
    }
  }

}

</script>

<style scoped>

.k-input {
  text-align: left;
}

.k-form-separator {
  height: 2.8em;
}

.k-field-name {
  word-wrap: break-word;
  padding-right: 1em;
  display: inline-block;
}

.k-input-group {
  background-color: #233038;
  margin-bottom: 1em;
}

</style>



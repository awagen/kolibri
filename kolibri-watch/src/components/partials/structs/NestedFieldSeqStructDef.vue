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
              :description="field.description">
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
              :description="field.description">
          </GenericSeqStructDef>
        </div>
      </template>

      <template v-if="(field.valueFormat instanceof MapInputDef)">
        <MapStructDef
            @value-changed="valueChanged"
            :name="field.name"
            :key-value-input-def="field.valueFormat.keyValueDef"
            :description="field.description"
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
          <NestedFieldSeqStructDef
              @value-changed="valueChanged"
              :conditional-fields="field.valueFormat.conditionalFields"
              :fields="field.valueFormat.fields"
              :name="field.name"
              :is-root="false"
              :description="field.description">
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
  NestedFieldSequenceInputDef, ConditionalFields, FieldDef
} from "@/utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {ref} from "vue";
import {useStore} from 'vuex';
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
    description: {type: String, required: false}
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
  methods: {},
  setup(props, context) {
    const store = useStore()
    // values for the permanent fields that are unconditional on other fields
    let fieldStates = ref({})
    // the field definition (make sure the default value is updated to the one in fieldStates)
    let fieldDef = ref({})
    // keeping the currently selected conditionalFields in separate state from permanent fields
    let selectedConditionalFields = ref([])
    let selectedConditionalFieldsStates = ref({})
    // keep track of the actual conditional fields and make sure the values set as default values
    // are updated on any update of selectedConditionalFieldsStates
    let selectedConditionalFieldsDef = ref({})
    props.fields.forEach(field => {
      fieldStates.value[field.name] = undefined
    })

    // TODO: ensure that default values are updates on every value change for the valueDef
    // of each FieldDef
    function updateConditionalFields(attributes) {
      // now check whether we have any conditional fields that carry the attribute.name as conditionField
      // and contain the current value of the conditionField as key in the mapping. If yes, add all FieldDef that
      // correspond to the current value if not already there.
      // NOTE: need to cherish default values update within the fields objects when a value is updated so
      // that we retain the values if an element is not deleted (e.g. when one or more elements are deleted)

      // determine which conditional fields are still needed
      console.debug(`all conditionalFields: ${props.conditionalFields.map(cField => JSON.stringify(cField.toObject()))}`)
      console.debug(props.conditionalFields)

      // check if the changed field value actually influences any conditional fields
      let existsConditionalField = props.conditionalFields
          .find(condField => condField.conditionField === attributes.name)

      // it's possible the changed value is not condition for conditional values, in which case we do not need
      // to manage those and can return
      if (existsConditionalField === undefined) {
        return
      }

      // TODO: here actually check whether the conditional fields for the conditionField value
      // suggest that any changes need to be made. If not, do not set the conditionalFields to empty, otherwise
      // conditional fields will suddenly disappear (might already be avoided with the above existsConditionalField check.
      // But check if there can be multiple conditionFields for which conditionalFields might be needed, in which case
      // we need to strictly limit removal to those fields not needed anymore (e.g comparison before change to after change)
      let validConditionalFields = props.conditionalFields
          .filter(condField => condField.conditionField === attributes.name)
          .filter(condField => condField.mapping.get(attributes.value) !== undefined)
          .flatMap(condField => condField.mapping.get(attributes.value))

      console.debug("valid conditional fields: " + validConditionalFields.map(field => (field === undefined) ? "undefined" : field.toObject()))

      let validConditionalFieldNames = validConditionalFields.map(field => field.name)
      let currentlySelectedFieldNames = selectedConditionalFields.value.map(field => field.name)
      let retainFieldNames = currentlySelectedFieldNames.filter(value => validConditionalFieldNames.includes(value))
      let addNewFieldNames = validConditionalFieldNames.filter(value => !currentlySelectedFieldNames.includes(value))
      let addConditionalFields = validConditionalFields.filter(field => addNewFieldNames.includes(field.name))

      console.info(`retainFieldNames: ${retainFieldNames}`)
      console.info(`addNewFieldNames: ${addNewFieldNames}`)

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
      addNewFieldNames.forEach(fieldName => selectedConditionalFieldsStates[fieldName] = undefined)
    }

    // the state keeping within the single components should take care of set values if state is altered,
    // otherwise we might need to set default values on the field's InputDefs (conditional or unconditional inputs)
    // TODO: ensure that default values are updates on every value change for the valueDef
    // of each FieldDef
    function valueChanged(attributes) {
      console.debug(`Nested struct def incoming value changed event: ${JSON.stringify(attributes)}`)
      let isUnconditionalField = true
      if (props.fields.map(field => field.name).includes(attributes.name)) {
        fieldStates.value[attributes.name] = attributes.value
      } else if (selectedConditionalFields.value.map(field => field.name).includes(attributes.name)) {
        isUnconditionalField = false
        selectedConditionalFieldsStates.value[attributes.name] = attributes.value
      }
      // update the conditional fields
      if (isUnconditionalField) {
        updateConditionalFields(attributes)
      }

      console.debug("Nested struct def conditional fields: ")
      console.debug(selectedConditionalFields.value)
      console.debug("Nested struct def conditional fields states: ")
      console.debug(selectedConditionalFieldsStates.value)

      // the actual javascript state object of the edits so far, contains all structures nested
      // into this one
      let combinedValue = Object.assign({}, fieldStates.value, selectedConditionalFieldsStates.value)
      // the combined structDef object with default values set to selections
      // TODO: this needs propagations of updates across single editable elements before this will work
      // e.g all valueChanged calls will need to update the object and the structDef class with default values
      // set to current values
      // TODO: also, fieldStates and selectedConditionalFieldStates do not reflect the
      // struct defs with default values but pure named values, thus passing them as fields / conditional
      // fields does not work
      let elementId = props.isRoot ? "root" : props.name + "-" + props.position
      let currentStructDefState = new NestedFieldSequenceInputDef(
          elementId,
          fieldStates.value,
          selectedConditionalFieldsStates.value
      )

      // TODO: the structDef can be either single def or can be list of structdefs
      context.emit('valueChanged', {
        name: props.name,
        value: combinedValue,
        structDef: currentStructDefState,
        position: props.position
      })
    }

    return {
      SingleValueInputDef,
      valueChanged,
      SeqInputDef,
      NestedFieldSequenceInputDef,
      KeyValuePairInputDef,
      KeyValueInputDef,
      MapInputDef,
      selectedConditionalFields
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



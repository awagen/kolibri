<template>

  <!-- NOTE: key needed to cause rerender on change of
   conditional fields -->
  <template :key="childKeyValue + '-' + field.value.valueFormat.elementId"
    v-for="(field, index) in [...toRefs(state.usedFields), ...toRefs(state.selectedConditionalFields)]">

    <div class="form-group k-input-group">

      <template v-if="(field.value.valueFormat instanceof SingleValueInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.value.description"/>
          <label class="form-label k-field-name"
                 :for="field.value.valueFormat.elementId + '-' + field.value.name + '-' + position + '-' + index">{{
              field.value.name
            }}</label>
        </div>

        <div :id="field.value.valueFormat.elementId + '-' + field.value.name + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <SingleValueStructDef
              @valueChanged="valueChanged"
              @valueConfirm="valueConfirm"
              :name="field.value.name"
              :element-def="field.value.valueFormat"
              :position="position * 100 + index"
              :description="field.value.description"
              :init-with-value="getMapValueForKey(field.value.name)"
          >
          </SingleValueStructDef>
        </div>
      </template>

      <template v-if="(field.value.valueFormat instanceof SeqInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.value.description"/>
          <label class="form-label k-field-name"
                 :for="field.value.valueFormat.elementId + '-' + field.value.name + '-' + position + '-' + index">{{
              field.value.name
            }}</label>
        </div>
        <div :id="field.value.valueFormat.elementId + '-' + field.value.name + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <GenericSeqStructDef
              @value-changed="valueChanged"
              :name="field.value.name"
              :input-def="field.value.valueFormat.inputDef"
              :position="position * 100 + index"
              :description="field.value.description"
              :init-with-value="getMapValueForKey(field.value.name)"
          >
          </GenericSeqStructDef>
        </div>
      </template>

      <template v-if="(field.value.valueFormat instanceof MapInputDef)">
        <MapStructDef
            @value-changed="valueChanged"
            :name="field.value.name"
            :key-value-input-def="field.value.valueFormat.keyValueDef"
            :description="field.value.description"
            :init-with-value="getMapValueForKey(field.value.name)"
        >
        </MapStructDef>
      </template>

      <template v-if="(field.value.valueFormat instanceof NestedFieldSequenceInputDef)">
        <div class="col-3 col-sm-12">
          <DescriptionPopover :description="field.value.description"/>
          <label class="form-label k-field-name"
                 :for="field.value.valueFormat.elementId + '-' + field.value.name + '-' + position + '-' + index">{{
              field.value.name
            }}</label>
        </div>
        <div :id="field.value.valueFormat.elementId + '-' + field.value.name  + '-' + position + '-' + index"
             class="k-input col-9 col-sm-12">
          <!-- note: we use v-model as shorthand for providing update method
           which we will use in the counter increase event emit to the component
           itself -->
          <NestedFieldSeqStructDef
              @value-changed="valueChanged"
              :conditional-fields="field.value.valueFormat.conditionalFields"
              :fields="field.value.valueFormat.fields"
              :name="field.value.name"
              :is-root="false"
              :description="field.value.description"
              :position="position * 100 + index"
              :init-with-value="getMapValueForKey(field.value.name)"
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
} from "../../../utils/dataValidationFunctions";
import SingleValueStructDef from "./SingleValueStructDef.vue";
import {reactive, ref, toRefs} from "vue";
import {useStore} from 'vuex';
import _ from "lodash";
import {
  safeGetMapValueForKey,
  getAllValidConditionalFieldDefs, getUpdatedConditionalFields
} from "../../../utils/baseDatatypeFunctions.ts";
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

    getMapValueForKey(fieldName) {
      return safeGetMapValueForKey(this.state.currentValues, fieldName, undefined)
    }

  },
  setup(props, context) {
    const store = useStore()

    let childKeyValue = ref(0)

    let initVals = (props.initWithValue !== undefined && props.initWithValue !== null) ? props.initWithValue : {}
    let unconditionalFieldValues = getCurrentValuesForNamesKeyValueMap(props.fields.map(x => x.name), initVals)
    let allValidConditionalFields = getAllValidConditionalFieldDefs(unconditionalFieldValues, props.conditionalFields)
    let state = reactive({
      usedFields: props.fields.map(x => x.copy()),
      usedConditionalFields: props.conditionalFields.map(x => x.copy()),
      currentValues: _.cloneDeep(initVals),
      unconditionalValues: unconditionalFieldValues,
      selectedConditionalFields: allValidConditionalFields
    })

    function increaseChildKeyCounter() {
      console.debug(`increasing childKeyValue: ${childKeyValue.value + 1}`)
      childKeyValue.value = childKeyValue.value + 1
    }

    function getCurrentValuesForNamesKeyValueMap(fieldNames, currentValues) {
      return Object.fromEntries(fieldNames.map(x => [x, currentValues[x]]))
    }

    function valueConfirm(attributes) {
      promoteCurrentStateUp()
    }

    function valueChanged(attributes) {
      let unconditionalField = isUnconditionalField(attributes.name)
      if (!unconditionalField) {
        // conditional field, we can just update its value without taking possible conditionals into account
        state.currentValues[attributes.name] = attributes.value
      }
      else {
        // in this case it's a conditional field, so we got to take into account the affected conditional fields
        let updateValue = {}
        updateValue[attributes.name] = attributes.value
        let updatedUnconditionalFields = Object.assign({},
            getCurrentValuesForNamesKeyValueMap(state.usedFields.map(x => x.name), state.currentValues),
            updateValue)
        const {updatedConditionalFields, updatedConditionalFieldStates} = getUpdatedConditionalFields(
            state.usedConditionalFields,
            updatedUnconditionalFields,
            state.currentValues,
            attributes.name,
            attributes.value)
        state.currentValues[attributes.name] = attributes.value
        state.unconditionalValues = getCurrentValuesForNamesKeyValueMap(props.fields.map(x => x.name), state.currentValues)
        for (const [key, value] of Object.entries(updatedConditionalFieldStates)) {
          state.currentValues[key] = value
        }
        // check if conditional fields changed in any way
        state.selectedConditionalFields = updatedConditionalFields.map(x => x.copy())

        // we only recreate the rendered part in this particular case where the changed attribute is a
        // unconditional one and the value is a string (which qualifies it to change conditional values. We need
        // to recreate children with the updated values in this case to ensure correct display of input elements)
        if (_.isString(attributes.value) && state.usedConditionalFields.length > 0 && !props.isRoot) {
          increaseChildKeyCounter()
        }

      }

      promoteCurrentStateUp()

    }

    function isUnconditionalField(fieldName) {
      return state.usedFields.map(field => field.name).includes(fieldName)
    }

    function promoteCurrentStateUp() {
      // if it's a root element,
      // update the global state of this object (merging the unconditional and the conditional fields)
      // otherwise just emit the state update to the parent
      if (props.isRoot) {
        let nestedInputDef = new NestedFieldSequenceInputDef(
            "root",
            state.usedFields,
            state.usedConditionalFields
        )
        store.commit("updateCurrentJobDefState", {
          jobDefStateObj: state.currentValues,
          jobDefState: nestedInputDef
        })
      } else {
        context.emit('valueChanged', {
          name: props.name,
          value: state.currentValues,
          position: props.position
        })
      }
    }

    return {
      SingleValueInputDef,
      valueChanged,
      valueConfirm,
      SeqInputDef,
      NestedFieldSequenceInputDef,
      KeyValuePairInputDef,
      KeyValueInputDef,
      MapInputDef,
      childKeyValue,
      state,
      toRefs
    }
  }

}

</script>

<style scoped>

label {
  max-width: 100%;
}

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



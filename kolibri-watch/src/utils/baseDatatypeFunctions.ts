import {
    ConditionalFields, FieldDef,
    InputDef,
    MapInputDef,
    NestedFieldSequenceInputDef,
    SeqInputDef,
    SingleValueInputDef
} from "./dataValidationFunctions";
import {LodashIsInteger} from "lodash/fp";
import {preservingJsonFormat} from "./formatFunctions";
import _ from "lodash";

function safeGetArrayValueAtIndex(arr: Array<any>, index: LodashIsInteger, defaultValue: any) {
    let value = defaultValue
    if (arr.length > 0) {
        value = arr[index]
    }
    return value
}

function safeGetMapValueForKey(dictionary: Object, key: string, defaultValue: any) {
    let value = defaultValue
    if (dictionary.hasOwnProperty(key)) {
        value = dictionary[key]
    }
    return value
}

function safeFillDefaultValueForField(field: FieldDef, data: Object) {
    safeFillDefaultValue(field.valueFormat, data, field.name)
}

function safeFillDefaultValue(inputDef: InputDef, data: Object, key: string) {
    let value = undefined
    if (data !== undefined && data.hasOwnProperty(key)) {
        value = data[key]
    }
    if (inputDef instanceof SingleValueInputDef) {
        inputDef.defaultValue = value
    }
    else if (inputDef instanceof SeqInputDef) {
        inputDef.inputDef.defaultValue = value
    }
    else if (inputDef instanceof MapInputDef) {
        inputDef.keyValueDef.defaultValue = value
    }
    else if (inputDef instanceof NestedFieldSequenceInputDef) {
        let fields = inputDef.fields
        let conditionalFields: Array<ConditionalFields> = inputDef.conditionalFields
        fields.forEach(field => {
            safeFillDefaultValue(field.valueFormat, value, field.name)
        })
        getAllValidConditionalFieldDefs(data, conditionalFields).forEach((conditionalField: FieldDef) => {
            safeFillDefaultValue(conditionalField.valueFormat, value, conditionalField.name)
        })
    }
}

/**
 * Iterates over all key value pairs of non-conditional fields (those are the only ones that can cause
 * an conditional field to become activated) and check which conditional fields would need to be made active
 * (e.g since they are contained in the mapping of the condition
 * @returns {*[]}
 */
function getAllValidConditionalFieldDefs(fieldStates: Object, conditionalFields: Array<ConditionalFields>): Array<FieldDef> {
    let validConditionalFieldDefs: Array<FieldDef> = []
    let validConditionalFieldNames: Array<string> = []
    for (const [key, value] of Object.entries(fieldStates)) {
        let vcFields = getValidConditionalFieldDefsForConditionField(conditionalFields, key, value)
        vcFields.forEach((element: FieldDef) => {
            if (!(validConditionalFieldNames.includes(element.name))) {
                validConditionalFieldDefs.push(element)
                validConditionalFieldNames.push(element.name)
            }
            else {
                console.debug(`SKIPPING ADDING FIELD: ${preservingJsonFormat(element.toObject())}, 
                since name in already collected names '${preservingJsonFormat(validConditionalFieldNames)}'`)
            }
        })
    }
    return validConditionalFieldDefs
}

/**
 * Given a name and value of a condition field, find those conditional fields that
 * a) depend on the passed field name and b) there exists a mapping for the value of the condition field that
 * contains this field
 * @param conditionalFields - conditional fields to look in
 * @param conditionFieldName - name of the field to search for dependent conditional fields
 * @param conditionFieldValue - value of the field to search for dependent conditional fields
 * @returns Array of FieldDef objects that are valid for the passed name and value of the conditionField
 */
function getValidConditionalFieldDefsForConditionField(conditionalFields: Array<ConditionalFields>, conditionFieldName: string, conditionFieldValue: any) {
    return conditionalFields
        .filter(condField => condField.conditionField === conditionFieldName)
        .filter(condField => condField.mapping.get(conditionFieldValue) !== undefined)
        .flatMap(condField => condField.mapping.get(conditionFieldValue))
        .map(field => field.copy())
}

/**
 * check whether we have any conditional fields that carry the attribute.name as conditionField
 * and contain the current value of the conditionField as key in the mapping. If yes, add all FieldDef that
 * correspond to the current value if not already there.
 * NOTE: need to cherish default values update within the fields objects when a value is updated so
 * that we retain the values if an element is not deleted (e.g. when one or more elements are deleted)
 * @param usedConditionalFields
 * @param unconditionalFieldStates
 * @param currentValues
 * @param attributeName
 * @param attributeValue
 * @param defaultValue
 */
function getUpdatedConditionalFields(usedConditionalFields: Array<ConditionalFields>,
                                     unconditionalFieldStates: Object,
                                     currentValues: Object,
                                     attributeName: string,
                                     attributeValue: any,
                                     defaultValue: any = undefined) {
    // extract all conditional fields that are valid as per the current settings of the non-conditional fields (assuming the key attributeName already has set
    // value attributeValue in fieldStates.value)
    let changedValues = {}
    changedValues[attributeName] = attributeValue
    let updatedCurrentValues = Object.assign({}, currentValues, changedValues)
    let updatedConditionalFieldStates = Object.assign({}, unconditionalFieldStates, changedValues)
    let validConditionalFieldDefs = getAllValidConditionalFieldDefs(updatedConditionalFieldStates, usedConditionalFields)
    let validConditionalFieldValues = Object.fromEntries(validConditionalFieldDefs
        .map(field => [field.name, updatedCurrentValues[field.name]]))
    // check if any value is affected by the change of (attributeName, attributeValue). If not, nothing to do here
    let affectedConditionalFieldDefs = getAllValidConditionalFieldDefs(changedValues, usedConditionalFields)
    if (affectedConditionalFieldDefs.length === 0) {
        return {updatedConditionalFields: validConditionalFieldDefs, updatedConditionalFieldStates: validConditionalFieldValues};
    }
    // update the selected conditional field states. We only need to change the ones that are affected by the change
    // setting them
    let changedSelectedConditionalFieldStates = Object.fromEntries(affectedConditionalFieldDefs.map(field => {
        let currentFieldValue = updatedCurrentValues[field.name]
        let validationFunction = field.valueFormat.getInputValidation().validationFunction
        let isValid = (currentFieldValue != undefined) ? validationFunction(currentFieldValue).isValid : false
        if (currentFieldValue != undefined && isValid) {
            return [field.name, currentFieldValue]
        }
        return [field.name, defaultValue]
    }))
    let resultingConditionalFieldStates = Object.assign(_.cloneDeep(validConditionalFieldValues), changedSelectedConditionalFieldStates)
    // now set the states, first the actual status and then the selected field defs corresponding to the values
    return {
        updatedConditionalFields: validConditionalFieldDefs,
        updatedConditionalFieldStates: resultingConditionalFieldStates
    }
}



export {
    safeGetArrayValueAtIndex,
    safeGetMapValueForKey,
    getValidConditionalFieldDefsForConditionField,
    getAllValidConditionalFieldDefs,
    safeFillDefaultValue,
    safeFillDefaultValueForField,
    getUpdatedConditionalFields
}
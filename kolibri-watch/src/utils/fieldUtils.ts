import {ConditionalFields, FieldDef, InputDef} from "./dataValidationFunctions";


/**
 * update the default value within a specific conditional field def in the
 * conditional fields
 * @param conditionalFields
 * @param conditionValue
 * @param fieldName
 * @param newValue
 * @param inPlace
 */
function setConditionalFieldMappingValue(conditionalFields: ConditionalFields,
                                         conditionValue: string,
                                         fieldName: string,
                                         newValue: any,
                                         inPlace: Boolean = false): ConditionalFields {
    let usedConditionalFields = conditionalFields
    if (!inPlace) {
        usedConditionalFields = conditionalFields.copy()
    }
    let fieldsForConditionValue = usedConditionalFields.mapping.get(conditionValue)
    if (fieldsForConditionValue === undefined) {
        console.warn(`Could not find conditional field mapping for conditionValue ${conditionValue}`)
        return;
    }
    let fieldDef = fieldsForConditionValue.find(fieldDef => fieldDef.name === fieldName)
    if (fieldDef === undefined) {
        return;
    }
    let newValueFormat = fieldDef.valueFormat.copy(fieldDef.valueFormat.elementId, newValue)
    let newFieldDef = new FieldDef(fieldDef.name, newValueFormat, fieldDef.required, fieldDef.description)
    let fieldDefIndex = fieldsForConditionValue.indexOf(fieldDef)
    if (fieldDefIndex < 0) {
        console.error("didnt find field def index to update default value for " +
            "value format of conditional value")
        return;
    }
    fieldsForConditionValue[fieldDefIndex] = newFieldDef
    return usedConditionalFields
}

/**
 *
 * @param fieldDef
 * @param value
 * @param inPlace
 */
function setFieldValue(fieldDef: FieldDef, value: any, inPlace: Boolean = false): FieldDef {
    if (inPlace) {
        fieldDef.valueFormat.defaultValue = value
        return fieldDef
    }
    let newValueFormat = fieldDef.valueFormat.copy(
        fieldDef.valueFormat.elementId,
        value
    )
    return new FieldDef(
        fieldDef.name,
        newValueFormat,
        fieldDef.required,
        fieldDef.description
    )
}

function setInputDefDefaultValue(inputDef: InputDef, defaultValue: any, inPlace: Boolean = false): InputDef {
    let usedInputDef = inputDef
    if (!inPlace) {
        usedInputDef = inputDef.copy(inputDef.elementId)
    }
    usedInputDef.defaultValue = defaultValue
    return usedInputDef
}

export {
    setConditionalFieldMappingValue,
    setFieldValue,
    setInputDefDefaultValue
}
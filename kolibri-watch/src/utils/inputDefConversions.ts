import {
    BooleanInputDef,
    ChoiceInputDef,
    ConditionalFields,
    FieldDef,
    FloatChoiceInputDef,
    InputDef,
    InputType,
    KeyValueInputDef,
    MapInputDef,
    NestedFieldSequenceInputDef,
    NumberInputDef,
    SeqInputDef,
    StringInputDef
} from "./dataValidationFunctions";

// plain value types
const STRING_TYPE = "STRING"
const REGEX_TYPE = "REGEX"
const INT_TYPE = "INT"
const FLOAT_TYPE = "FLOAT"
const DOUBLE_TYPE = "DOUBLE"
const BOOLEAN_TYPE = "BOOLEAN"
// basically a STRING type fix a fixed value
const STRING_CONSTANT_TYPE = "STRING_CONSTANT"

// numerical values limited by minimum and maximum values
const MIN_MAX_INT_TYPE = "MIN_MAX_INT"
const MIN_MAX_FLOAT_TYPE = "MIN_MAX_FLOAT"
const MIN_MAX_DOUBLE_TYPE = "MIN_MAX_DOUBLE"

// choice from a few pre-defined values
const CHOICE_STRING_TYPE = "CHOICE_STRING"
const CHOICE_INT_TYPE = "CHOICE_INT"

// Sequence types, translates to arrays with values of the specified type
const STRING_SEQ_TYPE = "STRING_SEQ"
const INT_SEQ_TYPE = "INT_SEQ"
const SEQ_REGEX_TYPE = "SEQ_REGEX"
const GENERIC_SEQ_TYPE = "GENERIC_SEQ_FORMAT"
// Sequence types holding min/max bounded values of specified numeric type
const SEQ_MIN_MAX_INT_TYPE = "SEQ_MIN_MAX_INT"
const SEQ_MIN_MAX_FLOAT_TYPE = "SEQ_MIN_MAX_FLOAT"
const SEQ_MIN_MAX_DOUBLE_TYPE = "SEQ_MIN_MAX_DOUBLE"
// Sequence types allowing multiple selections from a specified choice for the specified type
const SEQ_CHOICE_STRING_TYPE = "SEQ_CHOICE_STRING"
const SEQ_CHOICE_INT_TYPE = "SEQ_CHOICE_INT"

// map-like formats
// nested type takes unconditional field definitions (each field consists of a name and
// a structural definition of possible input values)
// it further holds conditional mappings, where individual unconditional field names and their values
// map to further field definitions that need to be specified for that particular value of the unconditional field
const NESTED_TYPE = "NESTED"
// MAP is more flexible than NESTED in that it only takes a key format and a value format
// and allows arbitrary number of key-value pairs where each key satisfies the key format and
// every value satisfies the value format
const MAP_TYPE = "MAP"

const ALL_STRINGS_REGEX = ".*"
const KEY_REGEX = "regex"
const KEY_MIN = "min"
const KEY_MAX = "max"
const KEY_VALUE = "value"
const KEY_CHOICES = "choices"
const KEY_KEY_FORMAT = "keyFormat"
const KEY_NAME_FORMAT = "nameFormat"
const KEY_VALUE_FORMAT = "valueFormat"
const KEY_REQUIRED = "required"
const KEY_DESCRIPTION = "description"
const KEY_CONDITION_FIELD_ID = "conditionFieldId"
const KEY_MAPPING_ID = "mapping"
const KEY_FIELDS = "fields"
const KEY_CONDITIONAL_FIELDS_SEQ = "conditionalFieldsSeq"
const KEY_PER_ELEMENT_FORMAT = "perElementFormat"
const KEY_TYPE = "type"


type DefConverter = (obj: Object, elementId: string, position: number) => InputDef


function adjustElementIdToPosition(elementId: string, position: number): string {
    return `${elementId}-pos${position}`
}

// single level, plain inputs
const stringDefConverter: DefConverter = (obj, elementId, position) => {
    return new StringInputDef(adjustElementIdToPosition(elementId, position), ALL_STRINGS_REGEX)
}

const regexDefConverter: DefConverter = (obj, elementId, position) => {
    return new StringInputDef(adjustElementIdToPosition(elementId, position), obj[KEY_REGEX])
}

const intDefConverter: DefConverter = (obj, elementId, position) => {
    return new NumberInputDef(adjustElementIdToPosition(elementId, position), 1, Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER)
}

const floatDefConverter: DefConverter = (obj, elementId, position) => {
    return new NumberInputDef(adjustElementIdToPosition(elementId, position), 0.1, Number.MIN_VALUE, Number.MAX_VALUE)
}

const numberMinMaxDefConverter: DefConverter = (obj, elementId, position) => {
    let min = obj[KEY_MIN]
    let max = obj[KEY_MAX]
    let type = NumberInputDef.getType([min, max])
    let step = 1
    if (type === InputType.FLOAT) {
        step = 0.1
    }
    return new NumberInputDef(adjustElementIdToPosition(elementId, position), step, min, max)
}

const booleanDefConverter: DefConverter = (obj, elementId, position) => {
    return new BooleanInputDef(adjustElementIdToPosition(elementId, position))
}

const stringConstantDefConverter: DefConverter = (obj, elementId, position) => {
    let constant = obj[KEY_VALUE]
    let inputDef = new StringInputDef(elementId, "^" + constant + "$")
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

const choiceDefConverter: DefConverter = (obj, elementId, position) => {
    let choices = obj[KEY_CHOICES]
    let isBooleanType = choices.filter(choice => ![true, false].includes(choice)).length == 0
    let isStringType = choices.filter(choice => isNaN(choice)).length > 0
    let isNumberType = choices.filter(choice => isNaN(choice)).length == 0
    if (isBooleanType || isStringType) {
        return new ChoiceInputDef(elementId, choices)
    } else if (isNumberType) {
        let numericType = NumberInputDef.getType(choices)
        if (numericType == InputType.FLOAT) {
            return new FloatChoiceInputDef(elementId, choices)
        }
        return new ChoiceInputDef(adjustElementIdToPosition(elementId, position), choices)
    }
}


// multi-level / nested inputs (sequences or map-like)
const intSeqDefConverter: DefConverter = (obj, elementId, position) => {
    let inputDef = new NumberInputDef(elementId, 1, Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

const stringSeqDefConverter: DefConverter = (obj, elementId, position) => {
    let inputDef = new StringInputDef(elementId, ALL_STRINGS_REGEX)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

const regexSeqDefConverter: DefConverter = (obj, elementId, position) => {
    let inputDef = regexDefConverter(obj, elementId, position + 1)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

const seqMinMaxNumberDefConverter: DefConverter = (obj, elementId, position) => {
    let inputDef = numberMinMaxDefConverter(obj, elementId, position + 1)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

const seqChoiceDefConverter: DefConverter = (obj, elementId, position) => {
    let inputDef = choiceDefConverter(obj, elementId, position + 1)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), inputDef)
}

function objToFieldDef(obj: Object, elementId: string, position: number): FieldDef {
    return new FieldDef(
        obj[KEY_NAME_FORMAT][KEY_VALUE],
        objToInputDef(obj[KEY_VALUE_FORMAT], adjustElementIdToPosition(elementId, position), position),
        obj[KEY_REQUIRED],
        obj[KEY_DESCRIPTION]
    )
}

function objToConditionalFields(obj: Object, elementId: string, position: number): ConditionalFields {
    // the conditionField name
    let conditionalFieldId = obj[KEY_CONDITION_FIELD_ID]
    // mapping string value to Array[FieldDef]
    let mappingObj: Object = obj[KEY_MAPPING_ID]
    let fieldArrayMapping: Object = {}
    for (const [conditionalFieldValue, fieldDefObjArr] of Object.entries(mappingObj)) {
        fieldArrayMapping[conditionalFieldValue] = fieldDefObjArr
            .map(fieldDefObj => {
                let name = fieldDefObj[KEY_NAME_FORMAT][KEY_VALUE]
                return objToFieldDef(fieldDefObj, `${elementId}-${name}`, position)
            })
    }
    return new ConditionalFields(conditionalFieldId,  new Map(Object.entries(fieldArrayMapping)))


}

const nestedDefConverter: DefConverter = (obj, elementId, position) => {
    let fieldsObjArray = obj[KEY_FIELDS]
    let fieldsArray: Array<FieldDef> = fieldsObjArray.map(field => {
        let name = field[KEY_NAME_FORMAT][KEY_VALUE]
        return objToFieldDef(field, `${elementId}-${name}`, position + 1)
    })
    let conditionalFieldsObjArray = obj[KEY_CONDITIONAL_FIELDS_SEQ]
    let conditionalFieldsArray: Array<ConditionalFields> = conditionalFieldsObjArray.map(cfield => {
        return objToConditionalFields(cfield, elementId, position + 1)
    })
    return new NestedFieldSequenceInputDef(
        adjustElementIdToPosition(elementId, position),
        fieldsArray,
        conditionalFieldsArray
    )
}

const mapDefConverter: DefConverter = (obj, elementId, position) => {
    let keyFormat = objToInputDef(obj[KEY_KEY_FORMAT], adjustElementIdToPosition(elementId, position + 1), position + 1)
    let valueFormat = objToInputDef(obj[KEY_VALUE_FORMAT], adjustElementIdToPosition(elementId, position + 1), position + 1)
    let keyValueInputDef = new KeyValueInputDef(
        adjustElementIdToPosition(elementId, position),
        keyFormat,
        valueFormat
    )
    return new MapInputDef(adjustElementIdToPosition(elementId, position), keyValueInputDef)
}

const seqDefConverter: DefConverter = (obj, elementId, position) => {
    let perElementFormat: InputDef = objToInputDef(obj[KEY_PER_ELEMENT_FORMAT], adjustElementIdToPosition(elementId, position + 1), position + 1)
    console.debug("seqdef per-element format")
    console.debug(perElementFormat)
    console.debug("is nested")
    console.debug(perElementFormat instanceof NestedFieldSequenceInputDef)
    return new SeqInputDef(adjustElementIdToPosition(elementId, position), perElementFormat)
}

/**
 * Mapping function of an object to an InputDef.
 * Note that the matched type keys correspond to the types
 * as defined in the JsonStructDefsFormat of the Scala backend.
 * @param obj
 * @param elementId
 * @param position
 */
function objToInputDef(obj: Object, elementId: string, position: number): InputDef {
    switch (obj[KEY_TYPE]) {
        case STRING_TYPE:
            return stringDefConverter(obj, elementId, position)
        case REGEX_TYPE:
            return regexDefConverter(obj, elementId, position)
        case INT_TYPE:
            return intDefConverter(obj, elementId, position)
        case FLOAT_TYPE:
            return floatDefConverter(obj, elementId, position)
        case DOUBLE_TYPE:
            return floatDefConverter(obj, elementId, position)
        case BOOLEAN_TYPE:
            return booleanDefConverter(obj, elementId, position)
        case STRING_CONSTANT_TYPE:
            return stringConstantDefConverter(obj, elementId, position)
        case MIN_MAX_INT_TYPE:
            return numberMinMaxDefConverter(obj, elementId, position)
        case MIN_MAX_FLOAT_TYPE:
            return numberMinMaxDefConverter(obj, elementId, position)
        case MIN_MAX_DOUBLE_TYPE:
            return numberMinMaxDefConverter(obj, elementId, position)
        case CHOICE_STRING_TYPE:
            return choiceDefConverter(obj, elementId, position)
        case CHOICE_INT_TYPE:
            return choiceDefConverter(obj, elementId, position)
        case STRING_SEQ_TYPE:
            return stringSeqDefConverter(obj, elementId, position)
        case INT_SEQ_TYPE:
            return intSeqDefConverter(obj, elementId, position)
        case SEQ_REGEX_TYPE:
            return regexSeqDefConverter(obj, elementId, position)
        case GENERIC_SEQ_TYPE:
            return seqDefConverter(obj, elementId, position)
        case SEQ_MIN_MAX_INT_TYPE:
            return seqMinMaxNumberDefConverter(obj, elementId, position)
        case SEQ_MIN_MAX_FLOAT_TYPE:
            return seqMinMaxNumberDefConverter(obj, elementId, position)
        case SEQ_MIN_MAX_DOUBLE_TYPE:
            return seqMinMaxNumberDefConverter(obj, elementId, position)
        case SEQ_CHOICE_STRING_TYPE:
            return seqChoiceDefConverter(obj, elementId, position)
        case SEQ_CHOICE_INT_TYPE:
            return seqChoiceDefConverter(obj, elementId, position)
        case NESTED_TYPE:
            return nestedDefConverter(obj, elementId, position)
        case MAP_TYPE:
            return mapDefConverter(obj, elementId, position)
        default:
            throw `NoMatchingInputDefConverter for elementId '${elementId}, position '${position}' and' object '${JSON.stringify(obj)}'`
    }

}

export {
    objToFieldDef,
    objToInputDef,
    objToConditionalFields,
    STRING_TYPE,
    REGEX_TYPE,
    INT_TYPE,
    FLOAT_TYPE,
    DOUBLE_TYPE,
    BOOLEAN_TYPE,
    STRING_CONSTANT_TYPE,
    MIN_MAX_DOUBLE_TYPE,
    MIN_MAX_FLOAT_TYPE,
    MIN_MAX_INT_TYPE,
    CHOICE_INT_TYPE,
    CHOICE_STRING_TYPE,
    SEQ_MIN_MAX_DOUBLE_TYPE,
    SEQ_MIN_MAX_FLOAT_TYPE,
    SEQ_MIN_MAX_INT_TYPE,
    SEQ_REGEX_TYPE,
    STRING_SEQ_TYPE,
    INT_SEQ_TYPE,
    SEQ_CHOICE_INT_TYPE,
    SEQ_CHOICE_STRING_TYPE,
    GENERIC_SEQ_TYPE,
    MAP_TYPE,
    NESTED_TYPE,
    DefConverter,
    adjustElementIdToPosition,
    stringDefConverter,
    regexDefConverter,
    intDefConverter,
    floatDefConverter,
    numberMinMaxDefConverter,
    booleanDefConverter,
    stringConstantDefConverter,
    choiceDefConverter,
    intSeqDefConverter,
    stringSeqDefConverter,
    regexSeqDefConverter,
    seqMinMaxNumberDefConverter,
    seqChoiceDefConverter,
    nestedDefConverter,
    mapDefConverter,
    seqDefConverter
}




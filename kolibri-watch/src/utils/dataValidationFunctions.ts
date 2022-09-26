function getStringValidationByRegexFunction(regexStr: string): (any) => ValidationResult {
    let regex = new RegExp(regexStr)
    return function (str: any) {
        let isValid = regex.test(str)
        return new ValidationResult(isValid, isValid ? "" :
            `value '${str}' does not match regex '${regexStr}'`)
    }
}

function getValidationByMinMax(min,
                               max,
                               expectedType: InputType = InputType.INT): (any) => ValidationResult {
    return function (val: any) {
        // nothing entered yet or clearing
        if (val === "") {
            return new ValidationResult(true, "")
        } else if (expectedType === InputType.INT && !Number.isInteger(Number(val))) {
            return new ValidationResult(false,
                `value ${val} expected to be Integer, but is not`)
        } else if (min !== undefined && val < min) {
            return new ValidationResult(false,
                `value ${val} is outside boundaries given by min=${min} / max=${max}`)
        } else if (max !== undefined && val > max) {
            return new ValidationResult(false,
                `value ${val} is outside boundaries given by min=${min} / max=${max}`)
        }
        return new ValidationResult(true, "")
    }
}

function getChoiceValidationFunction(choices: Array<any>): (any) => ValidationResult {
    return function (val: any) {
        if (choices.includes(val)) {
            return new ValidationResult(true, "")
        }
        return new ValidationResult(false, `value '${val}' is not in valid choices: '${choices}'`)
    }
}

function getFloatChoiceValidationFunction(choices: Array<number>, accuracy: Number = 0.0001): (any) => ValidationResult {
    return function (val: any) {
        let parsedValue: number = (typeof val === 'string') ? parseFloat(val) : val
        if (typeof parsedValue !== 'number') {
            return new ValidationResult(false, `value '${val}' is not a number`)
        }
        const item: any = choices.find((x) => (Math.abs(x - parsedValue) <= accuracy))
        if (item === undefined) {
            return new ValidationResult(false, `value '${val}' is not close enough within accurracy of '${accuracy}': '${choices}'`)
        }
        return new ValidationResult(true, "")
    }
}

function getSeqValidationFromValidationDef(validationDef: Object): (any) => ValidationResult {
    return function (val: any) {
        let singleElementValidation: (any) => ValidationResult = (any) => new InputValidation(validationDef).validationFunction.apply(any)
        if (!(val instanceof Array)) {
            return new ValidationResult(false, `value '${val}' is not array`)
        }
        let failedValidationResultsWithIndex: Array<string> = val
            .map((element, index) => [index, singleElementValidation.apply(element)])
            .filter((indexAndResult) => !indexAndResult[1].isValid)
            .map((indexAndFailResult) => `validation for index '${indexAndFailResult[0]}' failed with reason '${indexAndFailResult[1].failReason}'`)
        if (failedValidationResultsWithIndex.length == 0) {
            return new ValidationResult(true, "")
        }
        return new ValidationResult(false, "[" + failedValidationResultsWithIndex.join(", ") + "]")
    }
}

function getMatchAnyValidation(validationDefs: Array<Object>): (any) => ValidationResult {
    let inputValidations = validationDefs.map((def) => new InputValidation(def))
    return function (val: any) {
        let results = inputValidations.map((validation) => validation.validate(val))
        let passed = results.find(res => res.isValid)
        if (passed !== undefined) {
            return passed
        }
        return new ValidationResult(false, "no validation definition applies, one needed. Tried: " + validationDefs.join(", "))
    }
}


class ValidationResult {

    isValid: Boolean = true
    failReason: String = ""

    constructor(isValid: Boolean, failReason: string) {
        this.isValid = isValid
        this.failReason = failReason
    }

}

enum InputType {
    ANY,
    INT,
    FLOAT,
    STRING,
    BOOLEAN,
    // choice for arbitrary values, just need to be comparable by ===
    CHOICE,
    // choice for floating point, where right choice just needs to be sufficiently close to
    // a valid choice value (how close is needed is defined by accuracy parameter)
    FLOAT_CHOICE,
    // type for a sequence of values. They will usually all need to adhere
    // to a defined struct def format
    SEQ,
    // type where value is valid in case it matches any of a list
    // of given struct defs
    ANY_OF,
    MAP,
    KEY_VALUE,
    NESTED_FIELDS
}

class InputDef {
    elementId: string
    valueType: InputType
    validation: Object
    defaultValue: any = undefined

    constructor(elementId: string,
                valueType: InputType,
                validation: Object,
                defaultValue: any = undefined) {
        this.elementId = elementId
        this.valueType = valueType
        this.validation = validation
        this.defaultValue = defaultValue
    }

    toObject(): Object {
        return {
            "elementId": this.elementId,
            "valueType": this.valueType,
            "validation": this.validation,
            "defaultValue": this.defaultValue
        }
    }

    getInputValidation(): InputValidation {
        return new InputValidation(this.validation)
    }

    copy(elementId: string, defaultValue: any = this.defaultValue): InputDef {
        return new InputDef(elementId, this.valueType, this.validation, defaultValue)
    }


}

/**
 * Class representing single values
 */
class SingleValueInputDef extends InputDef {

    override copy(elementId: string, defaultValue: any = this.defaultValue): SingleValueInputDef {
        return super.copy(elementId, defaultValue)
    }
}

/**
 * Class representing a sequence of single values
 */
class SeqValueInputDef extends InputDef {

    override copy(elementId: string, defaultValue: any = this.defaultValue): SeqValueInputDef {
        return super.copy(elementId, defaultValue)
    }
}

/**
 * Class representing a key value pair
 */
class KeyValuePairInputDef extends InputDef {

    override copy(elementId: string, defaultValue: any = this.defaultValue): KeyValuePairInputDef {
        return super.copy(elementId, defaultValue)
    }

}

/**
 * Class representing multiple key-value pairs
 */
class MapValuesInputDef extends InputDef {

    override copy(elementId: string, defaultValue: any = this.defaultValue): MapValuesInputDef {
        return super.copy(elementId, defaultValue)
    }

}

/**
 * Class representing needed fields and possibility to define conditional fields, depending on the set values
 * for other fields
 */
class NestedFieldSequenceValuesInputDef extends InputDef {

    override copy(elementId: string, defaultValue: any = this.defaultValue): NestedFieldSequenceValuesInputDef {
        return super.copy(elementId, defaultValue)
    }

}

/**
 * Defining input definition for a sequence. The validation requires every element contained
 * in the resulting array needs to match the passed InputDef
 */
class SeqInputDef extends SeqValueInputDef {
    inputDef: InputDef = undefined

    constructor(elementId: string,
                inputDef: InputDef,
                defaultValue: any = undefined) {
        super(elementId, InputType.SEQ, {
            "type": InputType.SEQ,
            "validation": inputDef.validation
        }, defaultValue)
        this.inputDef = inputDef
    }

    override toObject(): Object {
        let obj = super.toObject()
        obj["inputDef"] = this.inputDef.toObject()
        return obj
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): SeqInputDef {
        return new SeqInputDef(elementId, this.inputDef, defaultValue)
    }
}

class AnyOfInputDef extends SingleValueInputDef {
    inputDefs: Array<InputDef> = undefined

    constructor(elementId: string,
                inputDefs: Array<InputDef>,
                defaultValue: any = undefined) {
        super(elementId, InputType.ANY_OF, {
            "type": InputType.ANY_OF,
            "validations": inputDefs.map((defs) => defs.validation)
        }, defaultValue)
        this.inputDefs = inputDefs
    }

    override toObject(): Object {
        let obj = super.toObject()
        obj["inputDefs"] = this.inputDefs.map((def) => def.toObject())
        return obj
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): AnyOfInputDef {
        return new AnyOfInputDef(elementId, this.inputDefs, defaultValue)
    }
}

class ChoiceInputDef extends SingleValueInputDef {
    choices = []

    constructor(elementId: string,
                choices: Array<any>,
                defaultValue: any = undefined) {
        super(elementId, InputType.CHOICE, {
            "type": InputType.CHOICE,
            "choices": choices
        }, defaultValue)
        this.choices = choices
    }

    override toObject(): Object {
        let obj = super.toObject()
        obj["choices"] = this.choices
        return obj
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): ChoiceInputDef {
        return new ChoiceInputDef(elementId, this.choices, defaultValue)
    }
}

class FloatChoiceInputDef extends SingleValueInputDef {
    choices = []

    constructor(elementId: string,
                choices: Array<Number>,
                accuracy: number = 0.0001,
                defaultValue: any = undefined) {
        super(elementId, InputType.FLOAT_CHOICE, {
            "type": InputType.FLOAT_CHOICE,
            "choices": choices,
            "accuracy": accuracy
        }, defaultValue)
        this.choices = choices
    }

    override toObject(): Object {
        let obj = super.toObject()
        obj["choices"] = this.choices
        return obj
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): FloatChoiceInputDef {
        return new FloatChoiceInputDef(elementId, this.choices, this.validation["accuracy"], defaultValue)
    }
}

class BooleanInputDef extends SingleValueInputDef {
    constructor(elementId: string,
                defaultValue: any = undefined) {
        super(elementId, InputType.BOOLEAN, {
            "type": InputType.BOOLEAN
        }, defaultValue);
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): BooleanInputDef {
        return new BooleanInputDef(elementId, defaultValue)
    }

}

class StringInputDef extends SingleValueInputDef {
    regex = ".*"

    constructor(elementId: string,
                regex: string,
                defaultValue: any = undefined) {
        super(elementId,
            InputType.STRING,
            {
                "type": InputType.STRING,
                "regex": regex
            }, defaultValue);
        this.regex = regex
    }

    override toObject(): Object {
        let obj = super.toObject()
        obj["regex"] = this.regex
        return obj
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): StringInputDef {
        return new StringInputDef(elementId, this.regex, defaultValue)
    }
}


class NumberInputDef extends SingleValueInputDef {
    step = 1
    min = 0
    max = 1

    private static areIntegers(values: Array<Number>): Boolean {
        let isIntegerArray = values.map((x) => Number.isInteger(x))
        return !isIntegerArray.includes(false)
    }

    static getType(values: Array<Number>): InputType {
        return NumberInputDef.areIntegers(values) ? InputType.INT : InputType.FLOAT
    }

    constructor(elementId: string,
                step: number = 1,
                min: number = undefined,
                max: number = undefined,
                defaultValue: any = undefined) {
        super(elementId, NumberInputDef.getType([step, min, max]), {
            "type": NumberInputDef.getType([step, min, max]),
            "min": min,
            "max": max
        }, defaultValue)
        this.step = step
        this.min = min
        this.max = max
    }

    override toObject(): Object {
        const obj = super.toObject()
        return Object.assign(obj, {"step": this.step, "min": this.min, "max": this.max})
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): NumberInputDef {
        return new NumberInputDef(elementId, this.step, this.min, this.max, defaultValue)
    }

}

/**
 * Input definition for key value type.
 * Note that default value must be array of length two, giving a value for key and one for value
 */
class KeyValueInputDef extends KeyValuePairInputDef {
    keyFormat: InputDef
    keyValue: string
    valueFormat: InputDef

    private static getKeyValidation(format: InputDef, value: string) {
        if (value !== undefined) {
            return {
                "type": InputType.STRING,
                "regex": "^" + value + "$"

            }
        }
        return format.validation
    }

    constructor(elementId: string,
                keyFormat: InputDef,
                valueFormat: InputDef,
                keyValue: string = undefined,
                defaultValue: any = undefined) {
        super(elementId, InputType.KEY_VALUE, {
            "type": InputType.KEY_VALUE,
            "keyValidation": KeyValueInputDef.getKeyValidation(keyFormat, keyValue),
            "valueValidation": valueFormat.validation
        }, defaultValue)
        this.keyValue = keyValue
        this.keyFormat = keyFormat
        this.valueFormat = valueFormat
    }

    override toObject(): Object {
        let obj = super.toObject();
        obj["keyFormat"] = (this.keyFormat !== undefined) ? this.keyFormat.toObject() : undefined
        obj["valueFormat"] = (this.valueFormat !== undefined) ? this.valueFormat.toObject() : undefined
        obj["keyValue"] = this.keyValue
        return obj
    }

    override copy(elementId: string, defaultValue: any = undefined): KeyValueInputDef {
        return new KeyValueInputDef(
            elementId,
            (defaultValue === undefined) ? this.keyFormat : this.keyFormat.copy(this.keyFormat.elementId, defaultValue[0]),
            (defaultValue === undefined) ? this.valueFormat : this.valueFormat.copy(this.valueFormat.elementId, defaultValue[1]),
            this.keyValue,
            defaultValue);
    }
}

/**
 * Allows zero or multiple key-value pairs where each key-value pair is subject to validation as defined
 * in the KeyValueInputDef (note: one value here is an array of size 2, [key, value]).
 *
 * The distinction to NestedFieldSequenceInputDef lies in the fact that in the NestedFieldSequenceInputDef
 * each entry of fields refers to a single key-value pair, while in the MapInputDef the key-value pairs can be arbitrarily
 * many.
 * In the NestedFieldSequenceInputDef also a field can be declared as optional and it provides the possibility of
 * defining conditional fields.
 */
class MapInputDef extends MapValuesInputDef {
    keyValueDef: KeyValueInputDef

    constructor(elementId: string,
                keyValueDef: KeyValueInputDef,
                defaultValue: any = undefined) {
        super(elementId, InputType.MAP, {
            "type": InputType.MAP,
            "keyValueValidation": keyValueDef.validation
        }, defaultValue)
        this.keyValueDef = keyValueDef
    }

    override toObject(): Object {
        let obj = super.toObject();
        obj["keyValueDef"] = this.keyValueDef.toObject()
        return obj
    }

    override copy(elementId: string, defaultValue: any = undefined): MapInputDef {
        return new MapInputDef(elementId, this.keyValueDef, defaultValue);
    }
}

/**
 * Defines a sequence of key-value mappings (fields) where each field can be required or optional (required = false)
 * Further, conditionalFields reference a field name and depending on the selected value for that field
 * a list of field definitions specific to that value (or none, which means no additional fields are needed for that
 * selected value of the conditionField. Note: the conditional fields are restricted to fields with string names and
 * string values
 */
class NestedFieldSequenceInputDef extends NestedFieldSequenceValuesInputDef {
    fields: Array<FieldDef>
    conditionalFields: Array<ConditionalFields>

    /**
     * From currently set field values derives the needed conditional values and returns the full set of
     * fields the need to be in a valid overall result object
     * @param currentValue
     */
    getAllNeededFields(currentValue: Map<string, any>): Array<FieldDef> {
        let conditionalFieldDefs: Array<FieldDef> = this.conditionalFields.flatMap(conditionalField => {
            let condValue = currentValue.get(conditionalField.conditionField)
            let conditionalFieldDefinitions: Array<FieldDef> = conditionalField.mapping.get(condValue)
            if (conditionalFieldDefinitions == undefined) {
                return []
            }
            return conditionalFieldDefinitions
        })
        return [...this.fields, ...conditionalFieldDefs];
    }

    override toObject(): Object {
        let obj = super.toObject();
        obj["fields"] = this.fields.map(field => field.toObject())
        obj["conditionalFields"] = this.conditionalFields.map(field => field.toObject())
        return obj
    }

    constructor(
        elementId: string,
        fields: Array<FieldDef>,
        conditionalFields: Array<ConditionalFields>,
        defaultValue: any = undefined) {
        super(elementId, InputType.NESTED_FIELDS, {
            "type": InputType.NESTED_FIELDS,
            "validationFunction": (val) => {
                let fieldValueAndValueValidationPairs: Array<Array<any>> = this.getAllNeededFields(val).map(field => [val.get(field.name), new InputValidation(field.valueFormat.validation)])
                let validationResults: Array<ValidationResult> = fieldValueAndValueValidationPairs.map(valueValidationPair => valueValidationPair[1].validationFunction.apply(valueValidationPair[0]))
                let failedValidationResults: Array<ValidationResult> = validationResults.filter(result => !result.isValid)
                if (failedValidationResults.length == 0) {
                    return new ValidationResult(true, "")
                }
                return new ValidationResult(false, failedValidationResults.map(result => result.failReason).join(" / "))
            }
        }, defaultValue)
        this.fields = fields
        this.conditionalFields = conditionalFields
    }

    override copy(elementId: string, defaultValue: any = this.defaultValue): NestedFieldSequenceValuesInputDef {
        return new NestedFieldSequenceInputDef(
            elementId,
            this.fields.map(field => field.copy()),
            this.conditionalFields.map(cField => cField.copy()),
            defaultValue
        );
    }
}


/**
 * defines a field via name format (validates field name value), value format (validates field value) and flag whether
 * the field is actually required or optional
 */
class FieldDef {
    name: string
    valueFormat: InputDef
    required: Boolean
    description: String

    toObject() {
        return {
            "name": this.name,
            "valueFormat": this.valueFormat.toObject(),
            "required": this.required,
            "description": this.description
        }
    }

    constructor(name: string,
                valueFormat: InputDef,
                required: Boolean,
                description: String = undefined) {
        this.name = name
        this.valueFormat = valueFormat
        this.required = required
        this.description = description
    }

    copy(name: string = this.name): FieldDef {
        return new FieldDef(
            name,
            this.valueFormat.copy(this.valueFormat.elementId),
            this.required,
            this.description)
    }
}

/**
 * Provides conditional mappings, e.g fields required that change depending on another field's value.
 * conditionField gives the field name of the field on whose values the other fields are conditioned on.
 * mapping provides the mapping of field value for the conditionField to the list of field definitions needed
 * for that specific value.
 */
class ConditionalFields {
    conditionField: string = undefined
    mapping: Map<string, Array<FieldDef>> = undefined

    toObject() {
        let mappingObj = {}
        this.mapping.forEach (function(value, key) {
            mappingObj[key] = value.map(def => def.toObject())
        })
        return {
            "conditionField": this.conditionField,
            "mapping": mappingObj
        }
    }

    constructor(conditionField: string,
                mapping: Map<string, Array<FieldDef>>) {
        this.conditionField = conditionField
        this.mapping = mapping
    }

    copy(): ConditionalFields {
        let mappingCopy = new Map<string, Array<FieldDef>>()
        this.mapping.forEach((entry, key) => {
            let copiedArray: Array<FieldDef> = entry.map(e => e.copy())
            mappingCopy.set(key, copiedArray)
        })
        return new ConditionalFields(
            this.conditionField,
            mappingCopy
        )
    }
}


/**
 * Specifies a validation of user input. Created by passing a dictionary with the right fields and values
 * per validation type
 */
class InputValidation {

    validationFunction: (any) => ValidationResult = undefined
    initParams: Object = undefined

    constructor(params: Object) {
        this.initParams = params
        let type = params["type"]
        if (type === InputType.INT) {
            let min = parseInt(params["min"])
            let max = parseInt(params["max"])
            this.validationFunction = (val) => {
                console.info("validating value: " + val)
                return getValidationByMinMax(min, max, type)(val)
            }
        } else if (type === InputType.FLOAT) {
            let min = parseFloat(params["min"])
            let max = parseFloat(params["max"])
            this.validationFunction = (val) => {
                console.info("validating value: " + val)
                return getValidationByMinMax(min, max, type)(val)
            }
        } else if (type === InputType.STRING) {
            let regexStr = params["regex"]
            this.validationFunction = (val) => {
                return getStringValidationByRegexFunction(regexStr)(val)
            }
        } else if (type === InputType.BOOLEAN) {
            this.validationFunction = (_) => new ValidationResult(true, "")
        } else if (type === InputType.CHOICE) {
            let choices: Array<any> = params["choices"]
            this.validationFunction = (val) => {
                return getChoiceValidationFunction(choices)(val)
            }
        } else if (type === InputType.FLOAT_CHOICE) {
            let choices: Array<number> = params["choices"]
            let accuracy: number = params["accuracy"]
            this.validationFunction = (val) => {
                return getFloatChoiceValidationFunction(choices, accuracy)(val)
            }
        } else if (type === InputType.SEQ) {
            let perElementValidation: Object = params["validation"]
            this.validationFunction = (val) => {
                return getSeqValidationFromValidationDef(perElementValidation)(val)
            }
        } else if (type == InputType.ANY_OF) {
            let validations: Array<Object> = params["validations"]
            this.validationFunction = (val) => {
                return getMatchAnyValidation(validations)(val)
            }
        } else if (type == InputType.KEY_VALUE) {
            let keyValidation = new InputValidation(params["keyValidation"])
            let valueValidation = new InputValidation(params["valueValidation"])
            this.validationFunction = (val) => {
                let keyResult: ValidationResult = keyValidation.validationFunction.apply(val[0])
                let valueResult: ValidationResult = valueValidation.validationFunction.apply(val[1])
                if (!keyResult.isValid || !valueResult.isValid) {
                    return new ValidationResult(false,
                        `key validation fail: ${keyResult.isValid ? "None" : keyResult.failReason}
                        value validation fail: ${valueResult.isValid ? "None" : valueResult.failReason}
                    `)
                }
                return new ValidationResult(true, "")
            }
        } else if (type == InputType.MAP) {
            let singleMappingValidation = new InputValidation(params["keyValueValidation"])
            this.validationFunction = (map) => {
                let keyValueEntries = Object.entries(map)
                let failedKeyValueValidations = keyValueEntries.map(kvPair => {
                    return singleMappingValidation.validationFunction.apply(kvPair)
                })
                    .filter(result => !result.isValid)
                if (failedKeyValueValidations.length > 0) {
                    return new ValidationResult(false, failedKeyValueValidations.map(result => result.failReason).join(" / "))
                } else {
                    return new ValidationResult(true, "")
                }
            }
        } else if (type == InputType.NESTED_FIELDS) {
            let validationFunction: (any) => ValidationResult = params["validationFunction"]
            this.validationFunction = (map) => validationFunction.apply(map)
        } else {
            this.validationFunction = (_) => new ValidationResult(true, "")
        }
    }

    validate(input: any): ValidationResult {
        return this.validationFunction(input)
    }

    toString(): string {
        return JSON.stringify(this.initParams)
    }


}

export {
    getStringValidationByRegexFunction,
    getValidationByMinMax,
    ValidationResult,
    InputType,
    InputValidation,
    InputDef,
    NumberInputDef,
    StringInputDef,
    BooleanInputDef,
    getChoiceValidationFunction,
    ChoiceInputDef,
    getFloatChoiceValidationFunction,
    FloatChoiceInputDef,
    SingleValueInputDef,
    SeqInputDef,
    SeqValueInputDef,
    KeyValueInputDef,
    MapInputDef,
    NestedFieldSequenceInputDef,
    NestedFieldSequenceValuesInputDef,
    FieldDef,
    ConditionalFields,
    KeyValuePairInputDef
}

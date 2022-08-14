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
    ANY_OF
}

class InputDef {
    name: string
    elementId: string
    valueType: InputType
    validation: Object

    constructor(name: string,
                elementId: string,
                valueType: InputType,
                validation: Object) {
        this.name = name
        this.elementId = elementId
        this.valueType = valueType
        this.validation = validation
    }

    toObject(): Object {
        return {
            "name": this.name,
            "elementId": this.elementId,
            "valueType": this.valueType,
            "validation": this.validation
        }
    }

    getInputValidation(): InputValidation {
        return new InputValidation(this.validation)
    }

    copy(name: string, elementId: string): InputDef {
        return new InputDef(name, elementId, this.valueType, this.validation)
    }


}

class IndexedInputDef {
    inputDef: InputDef = undefined
    index: Number = undefined
    constructor(inputDef: InputDef,
                index: Number) {
        this.inputDef = inputDef
        this.index = index
    }
}

/**
 * Class representing single values
 */
class SingleValueInputDef extends InputDef {
}

/**
 * Class representing a sequence of single values
 */
class SeqValueInputDef extends InputDef {}

/**
 * Defining input definition for a sequence. The validation requires every element contained
 * in the resulting array needs to match the passed InputDef
 */
class SeqInputDef extends SeqValueInputDef {
    inputDef: InputDef = undefined
    constructor(name: string,
                elementId: string,
                inputDef: InputDef) {
        super(name, elementId, InputType.SEQ, {
            "type": InputType.SEQ,
            "validation": inputDef.validation
        })
        this.inputDef = inputDef
    }

    override copy(name: string, elementId: string): InputDef {
        return new SeqInputDef(name, elementId, this.inputDef)
    }
}

class AnyOfInputDef extends SingleValueInputDef {
    inputDefs: Array<InputDef> = undefined

    constructor(name: string,
                elementId: string,
                inputDefs: Array<InputDef>) {
        super(name, elementId, InputType.ANY_OF, {
            "type": InputType.ANY_OF,
            "validations": inputDefs.map((defs) => defs.validation)
        })
        this.inputDefs = inputDefs
    }

    override copy(name: string, elementId: string): InputDef {
        return new AnyOfInputDef(name, elementId, this.inputDefs)
    }
}

class ChoiceInputDef extends SingleValueInputDef {
    choices = []

    constructor(name: string,
                elementId: string,
                choices: Array<any>) {
        super(name, elementId, InputType.CHOICE, {
            "type": InputType.CHOICE,
            "choices": choices
        })
        this.choices = choices
    }

    override copy(name: string, elementId: string): InputDef {
        return new ChoiceInputDef(name, elementId, this.choices)
    }
}

class FloatChoiceInputDef extends SingleValueInputDef {
    choices = []

    constructor(name: string,
                elementId: string,
                choices: Array<Number>,
                accuracy: number = 0.0001) {
        super(name, elementId, InputType.FLOAT_CHOICE, {
            "type": InputType.FLOAT_CHOICE,
            "choices": choices,
            "accuracy": accuracy
        })
        this.choices = choices
    }

    override copy(name: string, elementId: string): InputDef {
        return new FloatChoiceInputDef(name, elementId, this.choices)
    }
}

class BooleanInputDef extends SingleValueInputDef {
    constructor(name: string,
                elementId: string) {
        super(name, elementId, InputType.BOOLEAN, {
            "type": InputType.BOOLEAN
        });
    }

    override copy(name: string, elementId: string): InputDef {
        return new BooleanInputDef(name, elementId)
    }

}

class StringInputDef extends SingleValueInputDef {
    regex = ".*"

    constructor(name: string,
                elementId: string,
                regex: string) {
        super(name,
            elementId,
            InputType.STRING,
            {
                "type": InputType.STRING,
                "regex": regex
            });
    }

    override copy(name: string, elementId: string): InputDef {
        return new StringInputDef(name, elementId, this.regex)
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

    constructor(name: string,
                elementId: string,
                step: number = 1,
                min: number = undefined,
                max: number = undefined) {
        super(name, elementId, NumberInputDef.getType([step, min, max]), {
            "type": NumberInputDef.getType([step, min, max]),
            "min": min,
            "max": max
        })
        this.step = step
        this.min = min
        this.max = max
    }

    override toObject(): Object {
        const obj = super.toObject()
        return Object.assign(obj, {"step": this.step})
    }

    override copy(name: string, elementId: string): InputDef {
        return new NumberInputDef(name, elementId, this.step, this.min, this.max)
    }

}

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
        }
        else if (type == InputType.ANY_OF) {
            let validations: Array<Object> = params["validations"]
            this.validationFunction = (val) => {
                return getMatchAnyValidation(validations)(val)
            }
        }
        else {
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
    IndexedInputDef
}

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
        }
        else if (expectedType === InputType.INT && !Number.isInteger(Number(val))) {
            return new ValidationResult(false,
                `value ${val} expected to be Integer, but is not`)
        }
        else if (min !== undefined && val < min) {
            return new ValidationResult(false,
                `value ${val} is outside boundaries given by min=${min} / max=${max}`)
        }
        else if (max !== undefined && val > max) {
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
    // choice for arbitrary values, judt need to be comparable by ===
    CHOICE,
    // choice for floating point, where right choice just needs to be sufficiently close to
    // a valid choice value (how close is needed is defined by accuracy paramter)
    FLOAT_CHOICE
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
}

class ChoiceInputDef extends InputDef {
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
}

class FloatChoiceInputDef extends InputDef {
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
}

class BooleanInputDef extends InputDef {
    constructor(name: string,
                elementId: string) {
        super(name, elementId, InputType.BOOLEAN, {
            "type": InputType.BOOLEAN
        });
    }
}

class StringInputDef extends InputDef {
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
}


class NumberInputDef extends InputDef {
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
    }

    override toObject(): Object {
        const obj = super.toObject()
        return Object.assign(obj, {"step": this.step})
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
            this.validationFunction = (val) => new ValidationResult(true, "")
        }
        else if (type === InputType.CHOICE) {
            let choices: Array<any> = params["choices"]
            this.validationFunction = (val) => {
                return getChoiceValidationFunction(choices)(val)
            }
        }
        else if (type === InputType.FLOAT_CHOICE) {
            let choices: Array<number> = params["choices"]
            let accuracy: number = params["accuracy"]
            this.validationFunction = (val) => {
                return getFloatChoiceValidationFunction(choices, accuracy)(val)
            }
        }
        else {
            this.validationFunction = (val) => new ValidationResult(true, "")
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
    FloatChoiceInputDef
}
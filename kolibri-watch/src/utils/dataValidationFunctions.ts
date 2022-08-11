
function getStringValidationByRegexFunction(regexStr: string): (any) => ValidationResult {
    let regex = new RegExp(regexStr)
    return function(str: any){
        let isValid = regex.test(str)
        return new ValidationResult(isValid, isValid ? "" :
        `value '${str} does not match regex '${regexStr}'`)
    }
}

function getValidationByMinMax(min, max): (any) => ValidationResult {
    return function(val: any){
        // nothing entered yet or clearing
        if (val === "") {
            return new ValidationResult(true, "")
        }
        else if (min !== undefined && val < min) {
            return new ValidationResult(false,
                `value ${val} is outside boundaries given by min=${min} / max=${max}`)
        } else if (max !== undefined && val > max) {
            return new ValidationResult(false,
                `value ${val} is outside boundaries given by min=${min} / max=${max}`)
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
    INT,
    FLOAT,
    STRING,
    BOOLEAN
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
                // nothing entered yet or clearing
                // if (val === "") {
                //     return new ValidationResult(true, "")
                // }
                // else if (min !== undefined && val < min) {
                //     return new ValidationResult(false,
                //         `value ${val} is outside boundary: min=${min} / max=${max}`)
                // } else if (max !== undefined && val > max) {
                //     return new ValidationResult(false,
                //         `value ${val} is outside boundary: min=${min} / max=${max}`)
                // }
                // return new ValidationResult(true, "")
                return getValidationByMinMax(min, max)(val)
            }
        }
        else if (type === InputType.FLOAT) {
            let min = parseFloat(params["min"])
            let max = parseFloat(params["max"])
            this.validationFunction = (val) => {
                console.info("validating value: " + val)
                return getValidationByMinMax(min, max)(val)
            }

        }
        else if (type === InputType.STRING) {

        }
        else if (type === InputType.BOOLEAN) {

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
    InputValidation
}
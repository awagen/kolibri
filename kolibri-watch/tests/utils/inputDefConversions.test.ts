/**
 * @vitest-environment node
 */

import {expect, test} from "vitest";
import {objToInputDef} from "../../src/utils/inputDefConversions";
import {InputDef} from "../../src/utils/dataValidationFunctions";


test("correctly parse string input def", () => {
    // given
    const stringDef = {"type": "STRING"}
    // when
    const convertedInputDef: InputDef = objToInputDef(stringDef, "el1", 1)
    // then
    expect(convertedInputDef.getInputValidation().validate("a").isValid).true
    expect(convertedInputDef.getInputValidation().validate("1").isValid).true
})

test("correctly parse regex input def", () => {
    // given
    const regexDef = {"type": "REGEX", "regex": "^a\\w+"}
    // when
    const convertedInputDef: InputDef = objToInputDef(regexDef, "el1", 1)
    // then
    expect(convertedInputDef.getInputValidation().validate("a").isValid).false
    expect(convertedInputDef.getInputValidation().validate("ad").isValid).true
    expect(convertedInputDef.getInputValidation().validate("baa").isValid).false
})

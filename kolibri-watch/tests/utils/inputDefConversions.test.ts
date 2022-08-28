/**
 * @vitest-environment node
 */

import {expect, test} from "vitest";
import {objToInputDef} from "../../src/utils/inputDefConversions";
import {InputDef, NestedFieldSequenceInputDef} from "../../src/utils/dataValidationFunctions";


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

test("correctly parse full job definition", () => {
    // given
    const json = require('../testdata/searchEvaluationJobStructDef.json')
    // when
    const convertedInputDef: InputDef = objToInputDef(json, "test", 0)
    // then
    expect(convertedInputDef instanceof NestedFieldSequenceInputDef).true
})

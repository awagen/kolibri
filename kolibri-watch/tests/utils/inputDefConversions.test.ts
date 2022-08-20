/**
 * @vitest-environment node
 */

import {expect, test} from "vitest";
import {objToInputDef} from "../../src/utils/inputDefConversions";
import {InputDef} from "../../src/utils/dataValidationFunctions";

test("correctly convert json inputs to input defs", () => {
    // given
    const stringDef = {"type": "STRING"}
    // when
    const convertedInputDef: InputDef = objToInputDef(stringDef, "el1", 1)
    // then
    expect(convertedInputDef.getInputValidation().validate("a").isValid).true
    expect(convertedInputDef.getInputValidation().validate("1").isValid).true
})
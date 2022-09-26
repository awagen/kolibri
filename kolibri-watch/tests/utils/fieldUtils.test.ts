/**
 * @vitest-environment node
 */

import {expect, test} from "vitest";
import {ConditionalFields, FieldDef, StringInputDef} from "../../src/utils/dataValidationFunctions.ts";
import {setFieldValue, setConditionalFieldMappingValue, setInputDefDefaultValue} from "../../src/utils/fieldUtils.ts";


test("update field def valueFormat default value", () => {
    // given
    let valueFormat = new StringInputDef("e1", ".*")
    let fieldDef = new FieldDef("test",
        valueFormat,
        true,
        "testDesc")
    // when
    let resultInPlace = setFieldValue(fieldDef, "abc", true)
    let resultNotInPlace = setFieldValue(fieldDef, "def", false)
    // then
    expect(fieldDef.valueFormat.defaultValue).eq("abc")
    expect(resultInPlace.valueFormat.defaultValue).eq("abc")
    expect(resultNotInPlace.valueFormat.defaultValue).eq("def")
})

test("update conditional field", () => {
    // given
    let valueFormat = new StringInputDef("e1", ".*")
    let conditionalFieldEntries = {
        "val1": new Array(
            new FieldDef(
                "e",
                valueFormat,
                true,
                "")
        )
    }
    let conditionalFields = new ConditionalFields(
        "cField",
        new Map(Object.entries(conditionalFieldEntries))
    )
    // when
    let newCondValues = setConditionalFieldMappingValue(
        conditionalFields,
        "val1",
        "e",
        "abc",
        false
    )
    // then
    expect(conditionalFields.mapping.get("val1")[0].valueFormat.defaultValue).eq(undefined)
    expect(newCondValues.mapping.get("val1")[0].valueFormat.defaultValue).eq("abc")
})

test("set inputDef default value", () => {
    // given
    let inputDef = new StringInputDef("e1", ".*")
    // when, then
    let newValueNotInPlace = setInputDefDefaultValue(inputDef, "abc", false)
    expect(inputDef.defaultValue).eq(undefined)
    expect(newValueNotInPlace.defaultValue).eq("abc")
    let newValueInPlace = setInputDefDefaultValue(inputDef, "cde", true)
    expect(newValueInPlace.defaultValue).eq("cde")
    expect(inputDef.defaultValue).eq("cde")
})
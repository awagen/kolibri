/**
 * @vitest-environment node
 */

import {expect, test} from "vitest";
import {ConditionalFields} from "../../src/utils/dataValidationFunctions";
import {conditionalFieldDefs2} from "../testutils/inputDefHelper";
import {getUpdatedConditionalFields} from "../../src/utils/baseDatatypeFunctions";


let conditionalFields1 = conditionalFieldDefs2()
let unconditionalFieldObjectStates1 = {
    field1: "val1",
    entry0: "1"
}
let currentValues1 = {}
let attributeName = "attr1"
let attributeValue = 1



test("calculate conditional field state", () => {
    // given, when
    let {updatedConditionalFields, updatedConditionalFieldStates} = getUpdatedConditionalFields(
        conditionalFields1,
        unconditionalFieldObjectStates1,
        currentValues1,
        attributeName,
        attributeValue
    )
    // then
    expect(updatedConditionalFields.map(x => x.name)).toEqual(["field2", "field3", "entry1"])
    expect(updatedConditionalFieldStates).toEqual({field2: undefined, field3: undefined, entry1: undefined})
})
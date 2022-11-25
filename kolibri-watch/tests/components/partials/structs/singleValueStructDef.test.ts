/**
 * @vitest-environment happy-dom
 */

import {expect, test} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import SingleValueStructDef from "../../../../src/components/partials/structs/SingleValueStructDef.vue";
import {getInputElementId} from "../../../../src/utils/structDefFunctions";
import {getStringInputDefAndProps} from "../../../testutils/inputDefHelper";

const testValue = "teststr"

test("InputDef default value set as input value", () => {
    // given
    let {inputDef, props} = getStringInputDefAndProps("el1", ".*")
    inputDef.defaultValue = testValue
    const inputId = getInputElementId(inputDef.elementId, props.name, 1)
    const component = mount(SingleValueStructDef, {
        // @ts-ignore
        props: props
    })
    // when, then
    // @ts-ignore
    let inputContent = component.find(`#${inputId}`).element.value
    expect(inputContent).toContain(testValue)
    component.unmount()
})

test("initWithValue set as initial input value but changes ignored", async () => {
    // given
    let {inputDef, props} = getStringInputDefAndProps("el1", ".*", {initWithValue: testValue})
    const inputId = getInputElementId(inputDef.elementId, props.name, 1)

    const div = document.createElement('div')
    document.body.appendChild(div)

    const component = mount(SingleValueStructDef, {
        // @ts-ignore
        props: props,
        attachTo: div
    })
    // when, then
    await flushPromises();
    let inputElement = await component.find(`#${inputId}`).element
    // @ts-ignore
    let inputContent = inputElement.value
    expect(inputContent).toContain(testValue)
    // update value
    props['initWithValue'] = "newValue"
    // changing initWithValue props should not change anything
    // @ts-ignore
    component.setProps(props);
    await flushPromises();
    let newInputElement = await component.find(`#${inputId}`).element
    // @ts-ignore
    let newInputContent = newInputElement.value
    expect(newInputContent).toContain(testValue)
    // test that the change event was submitted
    expect(component.emitted()).toEqual({"valueChanged":[[{"name":"test-string-input","value":"teststr","position":1}]]})
    component.unmount()
})
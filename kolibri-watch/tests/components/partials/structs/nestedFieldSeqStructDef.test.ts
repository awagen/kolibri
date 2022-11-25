/**
 * @vitest-environment happy-dom
 */

import {beforeEach, expect, test} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import NestedFieldSeqStructDef from "../../../../src/components/partials/structs/NestedFieldSeqStructDef.vue";
import {
    conditionalFieldDefs1, fieldDefs1, getAllInputValues,
    getNestedFieldSequenceInputDef
} from "../../../testutils/inputDefHelper";
import { createStore } from 'vuex'
import {HTMLElement} from "happy-dom";
import {preservingJsonFormat} from "../../../../src/utils/formatFunctions";
import {objToFieldDef} from "../../../../src/utils/inputDefConversions";


const initValue1 = {
    "field1": "val1",
    "field2": "field2Val1",
    "field3": "field3Val1"
}

const initValue2 = {
    "field1": "val2",
    "field4": "field4Val1"
}

let store

beforeEach(() => {
    store = createStore({})
})

test("initialize to initWithValue but only take changes into account on complete refresh", async() => {
    //given
    let {nestedInputDef, nestedInputProps} = getNestedFieldSequenceInputDef(
        "nested",
        fieldDefs1(),
        conditionalFieldDefs1(),
        {
            initWithValue: initValue1
        })
    // when
    const div = document.createElement('div')
    document.body.appendChild(div)

    let component = mount(NestedFieldSeqStructDef, {
        // @ts-ignore
        props: nestedInputProps,
        attachTo: div,
        global: {
            plugins: [store]
        }
    })

    await flushPromises();
    // then
    let inputValues1 = getAllInputValues(div)
    expect(inputValues1).toEqual(["val1", "field2Val1", "field3Val1"])
    // setting new initWithValue should not change anything
    nestedInputProps['initWithValue'] = initValue2
    // @ts-ignore
    await component.setProps(nestedInputProps)
    await flushPromises();
    let inputValues2 = getAllInputValues(div)
    expect(inputValues2).toEqual(["val1", "field2Val1", "field3Val1"])
    let emitted1 = component.emitted()
    expect(emitted1).toEqual({"valueChanged":[[{"name":"test-nested-input","value":{"field1":"val1"},"position":1}],[{"name":"test-nested-input","value":{"field1":"val1","field2":"field2Val1"},"position":1}],[{"name":"test-nested-input","value":{"field1":"val1","field2":"field2Val1","field3":"field3Val1"},"position":1}]]})
    // now setting key should lead to recreation of the mounted component
    // thus we expect new values set. Note though that it seems we cannot
    // get an updated emitted(), likely a consequence of refreshing the component
    // via changing 'key' property
    nestedInputProps['key'] = "newKey"
    await component.setProps(nestedInputProps)
    await flushPromises();
    let inputValues3 = getAllInputValues(div)
    expect(inputValues3).toEqual(["val2", "field4Val1"])
})

test("request parameters input def should load initial values properly", async () => {
    // given
    // loading the definition and initial values in json format
    let initialValues = require("../../../testdata/requestParameters.json")
    let parameterDefinition = require("../../../testdata/requestParametersDefinition.json")
    console.info(`initialValues: ${preservingJsonFormat(initialValues)}`)
    console.info(`parameter def: ${preservingJsonFormat(parameterDefinition)}`)

    // that is the fieldDef of a single field, requestParameters.
    // passing this into fields argument for the NestedFieldSeqStructDef
    // and setting the initialValues as initWithValues should set the right
    // parameters
    let inputDef = objToFieldDef(parameterDefinition, "root", 0)
    console.info(`inputDef: ${preservingJsonFormat(inputDef)}`)

    let {nestedInputDef, nestedInputProps} = getNestedFieldSequenceInputDef(
        "nested",
        [inputDef],
        [],
        {
            initWithValue: initialValues
        })
    const div = document.createElement('div')
    document.body.appendChild(div)
    let component = mount(NestedFieldSeqStructDef, {
        // @ts-ignore
        props: nestedInputProps,
        attachTo: div,
        global: {
            plugins: [store]
        }
    })
    await flushPromises();
    let inputValues1 = getAllInputValues(div);
    console.info(`input values: ${preservingJsonFormat(inputValues1)}`)

    // when

})
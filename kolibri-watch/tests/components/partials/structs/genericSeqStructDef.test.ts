/**
 * @vitest-environment happy-dom
 */

import {beforeEach, expect, test} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import GenericSeqStructDef from "../../../../src/components/partials/structs/GenericSeqStructDef.vue";
import {
    getAllInputValues,
    getAllInputValuesWithFieldName,
    getSeqInputDefAndProps,
    getStringInputDefAndProps
} from "../../../testutils/inputDefHelper";
import {objToInputDef} from "../../../../src/utils/inputDefConversions";
import {preservingJsonFormat} from "../../../../src/utils/formatFunctions";
import {createStore} from "vuex";

const testValue = ["teststr1", "teststr2"]

let store

beforeEach(() => {
    store = createStore({})
})

test("InputDef default value set as input value", async () => {
    // given
    let {inputDef, props} = getStringInputDefAndProps("el1", ".*")
    let {seqInputDef, seqInputProps} = getSeqInputDefAndProps(
        "el2",
        inputDef,
        {initWithValue: testValue})
    // need to explicitly attach to div since otherwise the single values
    // fail when trying to access their own modal element
    const div = document.createElement('div')
    document.body.appendChild(div)
    const component = mount(GenericSeqStructDef, {
        // @ts-ignore
        props: seqInputProps,
        attachTo: div
    })
    await flushPromises();
    // need await here since the usage of defineAsyncComponent
    // in GenericSeqStructDef causes children to be mounted
    // after parent
    await new Promise(resolve => setTimeout(resolve, 250));
    // when, then
    let inputs = component.findAll(".k-seq-container input")
    // @ts-ignore
    let inputValues = Array.from(inputs.values()).map(x => x.element.value)
    expect(inputValues).toEqual(testValue)
    expect(component.emitted()).toEqual({"valueChanged":[[{"name":"test-seq-input","value":["teststr1","teststr2"],"position":1}],[{"name":"test-seq-input","value":["teststr1","teststr2"],"position":1}]]})
})

test("nested input def with default value correct deletion", async () => {
    // given
    // loading the definition and initial values in json format
    let initialValues = require("../../../testdata/requestParametersValueList.json")
    let inputDefinition = require("../../../testdata/requestParametersSingleFieldDefinition.json")
    // when
    let {seqInputDef, seqInputProps} = getSeqInputDefAndProps(
        "seq-input",
        objToInputDef(inputDefinition, "el1", 0),
        {initWithValue: initialValues})
    // then
    const div = document.createElement('div')
    document.body.appendChild(div)
    let component = mount(GenericSeqStructDef, {
        // @ts-ignore
        props: seqInputProps,
        attachTo: div,
        global: {
            plugins: [store]
        }
    })
    await flushPromises();
    await new Promise(resolve => setTimeout(resolve, 1000));
    // TODO: this just gives the values of all input values,
    // yet not the full substructure represented in html,
    // e.g nested field names would not appear because they are
    // not single leave input elements
    let inputValues1 = getAllInputValuesWithFieldName(div);
    console.info(`input values: ${preservingJsonFormat(inputValues1)}`)
    let inputs = component.findAll("input")
    console.info(`inputs: ${inputs.map(x => x.html())}`)
    // console.info(`div: ${div.outerHTML}`)
    // console.info(`component: ${component.html()}`)
    // console.info(`input values -2: ${component.vm.addedInputValues.map(x => preservingJsonFormat(x))}`)
})

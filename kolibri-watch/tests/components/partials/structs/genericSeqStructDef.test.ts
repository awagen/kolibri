/**
 * @vitest-environment happy-dom
 */

import {beforeEach, expect, test} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import GenericSeqStructDef from "../../../../src/components/partials/structs/GenericSeqStructDef.vue";
import {
    getAllInputValuesWithFieldName,
    getSeqInputDefAndProps,
    getStringInputDefAndProps
} from "../../../testutils/inputDefHelper";
import {objToInputDef} from "../../../../src/utils/inputDefConversions";
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
    await new Promise(resolve => setTimeout(resolve, 500));
    let nameValueSequence = getAllInputValuesWithFieldName(div);
    expect(nameValueSequence).toEqual([
        {"name":"type","value":"MAPPING"},{"name":"name","value":"keyId"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"FROM_ORDERED_VALUES_TYPE"},{"name":"type","value":"FROM_FILENAME_KEYS_TYPE"},{"name":"name","value":"keyId"},{"name":"directory","value":"data/fileMappingSingleValueTest"},{"name":"filesSuffix","value":".txt"},{"name":"name","value":"mapped_id"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"CSV_MAPPING_TYPE"},{"name":"values","value":"data/csvMappedParameterTest/mapping1.csv"},{"name":"column_delimiter","value":","},{"name":"key_column_index","value":0},{"name":"value_column_index","value":1},{"name":"name","value":"value"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"VALUES_FROM_NODE_STORAGE"},{"name":"identifier","value":"prefixToFilesLines1"},{"name":"key_mapping_assignments","value":0},{"name":"key_mapping_assignments","value":1},{"name":"key_mapping_assignments","value":0},{"name":"key_mapping_assignments","value":2},{"name":"type","value":"STANDALONE"},{"name":"name","value":"q"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"FROM_ORDERED_VALUES_TYPE"},{"name":"type","value":"FROM_FILES_LINES_TYPE"},{"name":"name","value":"q"},{"name":"file","value":"test-paramfiles/test_queries.txt"},{"name":"type","value":"STANDALONE"},{"name":"name","value":"a1"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"FROM_ORDERED_VALUES_TYPE"},{"name":"type","value":"FROM_VALUES_TYPE"},{"name":"name","value":"a1"},{"name":"values","value":"0.45"},{"name":"values","value":"0.32"},{"name":"type","value":"STANDALONE"},{"name":"name","value":"o"},{"name":"values_type","value":"URL_PARAMETER"},{"name":"type","value":"FROM_ORDERED_VALUES_TYPE"},{"name":"type","value":"FROM_RANGE_TYPE"},{"name":"name","value":"o"},{"name":"start","value":0},{"name":"end","value":2000},{"name":"stepSize","value":1}
    ])
})

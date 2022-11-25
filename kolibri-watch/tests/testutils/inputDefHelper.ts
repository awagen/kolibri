import {
    ConditionalFields,
    FieldDef,
    InputDef, NestedFieldSequenceInputDef,
    SeqInputDef,
    StringInputDef
} from "../../src/utils/dataValidationFunctions";
import {HTMLElement, IElement} from "happy-dom";


function getStringInputDefAndProps(elementId: string,
                                   regex: string,
                                   mergeProps: Object = {},
                                   inputName: string = "test-string-input",
                                   testDescription: string = "test-string-input-desc") {
    const inputDef = new StringInputDef(elementId, regex)
    const props = Object.assign({
        elementDef: inputDef,
        position: 1,
        name: inputName,
        description: testDescription,
    }, mergeProps)
    return {inputDef, props}
}

function getSeqInputDefAndProps(elementId: string,
                                inputDef: InputDef,
                                mergeProps: Object = {},
                                inputName: string = "test-seq-input",
                                testDescription: string = "test-seq-input-desc",
                                defaultValue: any = undefined) {
    const seqInputDef = new SeqInputDef(elementId, inputDef, defaultValue)
    const seqInputProps = Object.assign({
        inputDef: inputDef,
        position: 1,
        name: inputName,
        description: testDescription,
    }, mergeProps)
    return {seqInputDef, seqInputProps}
}

function getNestedFieldSequenceInputDef(elementId: string,
                                        fields: Array<FieldDef>,
                                        conditionalFields: Array<ConditionalFields>,
                                        mergeProps: Object = {},
                                        inputName: string = "test-nested-input",
                                        testDescription: string = "test-nested-input-desc",
                                        defaultValue: any = undefined) {
    const nestedInputDef = new NestedFieldSequenceInputDef(
        elementId,
        fields,
        conditionalFields,
        defaultValue
    )
    const nestedInputProps = Object.assign({
        fields: fields,
        conditionalFields: conditionalFields,
        name: inputName,
        isRoot: false,
        position: 1,
        description: testDescription
    }, mergeProps)
    return {nestedInputDef, nestedInputProps}

}

function stringInputDefAndProps1() {
    return getStringInputDefAndProps("el1", ".*")
}

function stringInputDefAndProps2() {
    return getStringInputDefAndProps("el2", ".*")
}

function stringInputDefAndProps3() {
    return getStringInputDefAndProps("el3", ".*")
}

function fieldDefs1() {
    return [
        new FieldDef("field1", stringInputDefAndProps1().inputDef, true)
    ]
}

function mapping1() {
    return {
        "val1": [
            new FieldDef("field2", stringInputDefAndProps1().inputDef, true),
            new FieldDef("field3", stringInputDefAndProps2().inputDef, true)
        ],
        "val2": [
            new FieldDef("field4", stringInputDefAndProps3().inputDef, true)
        ]
    }
}

function mapping2() {
    return {
        "1": [
            new FieldDef("entry1", stringInputDefAndProps1().inputDef, true),
        ],
        "2": [
            new FieldDef("entry2", stringInputDefAndProps2().inputDef, true),
            new FieldDef("entry3", stringInputDefAndProps3().inputDef, true)
        ]
    }
}

function conditionalFieldDefs1() {
    return [
        new ConditionalFields("field1", new Map(Object.entries(mapping1())))
    ]
}

function conditionalFieldDefs2() {
    return [
        new ConditionalFields("field1", new Map(Object.entries(mapping1()))),
        new ConditionalFields("entry0", new Map(Object.entries(mapping2())))
    ]
}

/**
 * function declaring input element as invalid if it is
 * a radio input and it is not checked.
 * All other input elements (e.g text, number,...) will pass as valid
 * @param element
 */
function isNoUncheckedRadioInput(element: IElement): boolean {
    let inputType = element.getAttribute("type")
    if (inputType === "radio" && element.getAttribute("checked") === null){
        return false;
    }
    return true;
}

function filterInputElements(inputElements: Array<IElement>) {
    return inputElements.filter(x => isNoUncheckedRadioInput(x))
}

function getAllInputValues(htmlElement: HTMLElement) {
    let allInputs = Array.from(htmlElement.querySelectorAll("input"))
    let filteredInputs = filterInputElements(allInputs)
    return filteredInputs.map(input => input.value)
}

function getAllInputValuesWithFieldName(htmlElement: HTMLElement) {
    let allInputs = Array.from(htmlElement.querySelectorAll("input"))
    let filteredInputs = filterInputElements(allInputs)
    return filteredInputs.map(input => {
        let value = input.value
        let prev = input
        do prev = prev.parentElement; while(prev && prev.querySelector(".k-field-name") === null);
        let name = (prev.querySelector(".k-field-name") !== null) ? prev.querySelector(".k-field-name").innerHTML : null;
        return {name: name, value: value}
    })
}


export {
    getStringInputDefAndProps,
    getSeqInputDefAndProps,
    getNestedFieldSequenceInputDef,
    stringInputDefAndProps1,
    stringInputDefAndProps2,
    stringInputDefAndProps3,
    fieldDefs1,
    mapping1,
    conditionalFieldDefs1,
    getAllInputValues,
    getAllInputValuesWithFieldName,
    conditionalFieldDefs2
}
function getInputElementId(elementId: string, name: string, position: Number) {
    return 'k-' + elementId + "-" + name + "-input-" + position
}

function getInputElementToastId(elementId: string, name: string, position: Number) {
    return 'k-' + elementId + "-" + name + '-msg-toast-' + position
}

function getInputElementToastContentId(elementId: string, name: string, position: Number) {
    return 'k-' + elementId + "-" + name + '-msg-toast-content-' + position
}

export {
    getInputElementId,
    getInputElementToastId,
    getInputElementToastContentId
}
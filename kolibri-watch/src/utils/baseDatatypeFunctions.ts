function saveGetArrayValueAtIndex(arr: Array<any>, index, defaultValue: any) {
    if (arr.length > 0) {
        return arr[index]
    }
    return defaultValue
}

function saveGetMapValueForKey(dictionary: Object, key: string, defaultValue: any) {
    if (dictionary.hasOwnProperty(key)) {
        let value = dictionary[key]
        return value
    }
    return defaultValue
}

export {saveGetArrayValueAtIndex,
    saveGetMapValueForKey}
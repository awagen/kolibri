function filteredResultsReduced(executionResult) {
    if (executionResult == null) {
        return []
    }
    let colNames = executionResult.columnNames
    let paramNames = executionResult.paramNames
    let paramIndices = paramNames.map(x => colNames.indexOf(x))
    let metricNames = executionResult.metricNames
        .map(x => "value-" + x)
    let metricIndices = metricNames.map(x => colNames.indexOf(x))
    let allIndices = paramIndices.concat(metricIndices)
    return {
        "columnNames": paramNames.concat(executionResult.metricNames),
        "dataLinesAsColumns": executionResult.dataLinesAsColumns
            .map(x => x.filter((y, i) => allIndices.includes(i)))
    }
}

/**
 * Pick the selectedData (as composed in the composer section of the DataCompose view)
 * and generate an array with json definitions reflecting the standalone data and mappings.
 * Note that the json definitions of the single data samples are already given within the
 * single selectedData samples under the key 'jsonDefinition'.
 * Each selectedData sample is an own json object by itself.
 * Following mapping rules:
 * 1) standalone values are mapped to their jsonDefinition value
 * 2) mapped values are transformed into mapping {
 * 'keyValues': json definition of key values,
 * 'mappedValues': list of json definitions of mapped values,
 * 'mappingKeyValueAssignments': list of tuples where x._1 = index of data used as keys,
 * x._2 = index + 1 of the mapped value (+ 1 since key provider is counted as index 0).
 * The mapped to index (x._1) is given by each mappedValue's "mappedToIndex" property
 * }
 * @param state
 */
function selectedDataToParameterValuesJson(selectedData){
    console.info("generating json")
    return selectedData.map(x => {
        if (x.type === "standalone") {
            return x.data.jsonDefinition
        }
        else if (x.type === "mapping") {
            return {
                "key_values": x.data.keyValues.jsonDefinition,
                "mapped_values": x.data.mappedValues.map(x => x.jsonDefinition),
                "key_mapping_assignments": x.data.mappedValues.map((value, index) => {
                    return [value.mappedToIndex, index + 1]
                })
            }
        }
    })
}

export {filteredResultsReduced, selectedDataToParameterValuesJson}
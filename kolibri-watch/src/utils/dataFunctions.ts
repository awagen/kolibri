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

export {filteredResultsReduced}
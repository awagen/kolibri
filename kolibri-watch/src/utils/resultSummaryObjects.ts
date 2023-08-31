import {formatValueArray, numberAwareComparison, numberAwareFormat} from "./dataFunctions";

/**
 * ID assigned to one or more value rows
 */
class RowsWithId {

    id: string
    rows: Array<Array<any>>

    constructor(id: string,
                rows: Array<Array<any>>) {
        this.id = id
        this.rows = rows
    }

    /**
     *
     * @param rowIndex
     * @param valueIndex
     */
    getValue(rowIndex, valueIndex): any | undefined {
        if (rowIndex >= this.rows.length || valueIndex >= this.rows[rowIndex].length) return undefined
        return this.rows[rowIndex][valueIndex]
    }

    toDict(): Object {
        return {
            id: this.id,
            rows: this.rows
        }
    }

    static fromDict(dict): RowsWithId {
        return new RowsWithId(
            dict["id"],
            dict["rows"]
        )
    }
}


class ResultSummary {

    // sort settings
    sortedById: boolean = false
    idSortDecreasing = true
    sortedByColumn: Array<boolean>
    columnSortDecreasing: Array<boolean>


    // the metric name for which this obj represents the summary
    metricName: string

    // min and max values for the measures (e.g to give a percentage
    // of max that can be used for result coloring and so on)
    minMeasureValue: Number
    maxMeasureValue: Number
    minObservedMeasureValue: Number
    maxObservedMeasureValue: Number

    // names of the value columns
    columnNames: Array<string>

    // available measures
    measureNames: Array<string>

    // every entry in the values array is assigned an id and
    // a number of value arrays. Here the row for measureName at index i
    // is to be found in the i-th row, while the value for columnName j
    // is found at the j-th value of the value row
    values: Array<RowsWithId>

    constructor(metricName: string,
                columnNames: Array<string>,
                measureNames: Array<string>,
                values: Array<RowsWithId>,
                minMeasureValue: Number = 0.0,
                maxMeasureValue: Number = 1.0) {
        this.metricName = metricName
        this.columnNames = columnNames
        this.measureNames = measureNames
        this.values = values
        this.sortedByColumn = columnNames.map(_ => false)
        this.columnSortDecreasing = columnNames.map(_ => true)
        this.minMeasureValue = minMeasureValue
        this.maxMeasureValue = maxMeasureValue
        this.minObservedMeasureValue = Math.min(...values.flatMap(rowsWithId => rowsWithId.rows.flatMap(row => {
            return Math.min(...row)
        })))
        this.maxObservedMeasureValue = Math.max(...values.flatMap(rowsWithId => rowsWithId.rows.flatMap(row => {
            return Math.max(...row)
        })))

    }

    /**
     * Find the data for a specific id
     *
     * @param id
     */
    getRowsForId(id: string): RowsWithId | undefined  {
        return this.values.find(x => x.id === id)
    }

    /**
     * Get row for specified id and measure.
     *
     * @param id
     * @param measure
     */
    getRowForIdAndMeasure(id: string, measure: string): Array<any> | undefined {
        let rowsForId = this.getRowsForId(id)
        let measureIndex = this.measureNames.indexOf(measure)
        if (rowsForId == undefined || measureIndex < 0) return undefined
        return rowsForId.rows[measureIndex]
    }

    idSort(decreasing: boolean) {
        let sorted = [...this.values].sort(function(data1, data2) {
            return numberAwareComparison(data1.id, data2.id)
        })
        if (decreasing) sorted.reverse()
        this.values = sorted
        // set sort-flags to be able to represent status in UI
        this.sortedByColumn = this.sortedByColumn.map(_ => false)
        this.sortedById = true
        this.idSortDecreasing = decreasing
    }

    sortByMeasureAndColumnByIndices(measureIndex: number, columnIndex: number, decreasing: boolean) {
        if (measureIndex >= this.measureNames.length  || columnIndex >= this.columnNames.length) {
            console.info(`Measure index '${measureIndex}' or columIndex '${columnIndex}' out of bounds, aborting sort`)
            return
        }
        let sorted = [...this.values].sort(function(data1, data2) {
            let value1 = data1.getValue(measureIndex, columnIndex)
            let value2 = data2.getValue(measureIndex, columnIndex)
            return numberAwareComparison(value1, value2)
        })
        if (decreasing) sorted.reverse()
        this.values = sorted
        // set sort-flags to be able to represent status in UI
        this.sortedByColumn = this.sortedByColumn.map(_ => false)
        this.sortedByColumn[columnIndex] = true
        this.columnSortDecreasing[columnIndex] = decreasing
        this.sortedById = false
    }

    /**
     * Sort by measure and column identifiers.
     *
     *
     * @param measure
     * @param columnName
     * @param decreasing
     */
    sortByMeasureAndColumnByNames(measure: string, columnName: string, decreasing: boolean): void {
        let measureIndex = this.measureNames.indexOf(measure)
        let columnIndex = this.columnNames.indexOf(columnName)
        if (measureIndex < 0 || columnIndex < 0) {
            console.info(`Measure '${measure}' or columnName '${columnName}' undefined, aborting sort`)
            return
        }
        this.sortByMeasureAndColumnByIndices(measureIndex, columnIndex, decreasing)
    }

    toDict(): Object {
        return {
            metricName: this.metricName,
            columnNames: this.columnNames,
            measureNames: this.measureNames,
            values: this.values.map(value => value.toDict()),
            minMeasureValue: this.minMeasureValue,
            maxMeasureValue: this.maxMeasureValue
        }
    }

    fromDict(dict): ResultSummary {
        return new ResultSummary(
            dict["metricName"],
            dict["columnNames"],
            dict["measureNames"],
            dict["values"].map(value => RowsWithId.fromDict(value)),
            dict["minMeasureValue"],
            dict["maxMeasureValue"]
        )
    }

}

/**
 * Filling the json result into data object where
 * functionality like sorting is baked into the object's methods
 * (see ResultSummary class)
 *
 * @param json
 * @returns {ResultSummary}
 */
function resultJsonToEffectEstimateResultSummary(json) {
    let resultsKey = "results"
    let effectEstimateKey = "parameterEffectEstimate"
    let ids = Object.keys(json[resultsKey])
    let effectMeasureNames = Object.keys(json[resultsKey][ids[0]][effectEstimateKey])
    let parameterNames = Object.keys(json[resultsKey][ids[0]][effectEstimateKey][effectMeasureNames[0]])
    effectMeasureNames.sort().reverse()
    parameterNames.sort().reverse()
    let rowsWithIdArray = ids.map(id => {
        let valueRowsForId = effectMeasureNames.map(effectMeasure => {
            let paramNameToEffectValue = json[resultsKey][id][effectEstimateKey][effectMeasure]
            return parameterNames.map(name => paramNameToEffectValue[name])
                .map(numb => numb.toFixed(4))
        })
        return new RowsWithId(id, valueRowsForId)
    })
    let metricName = json["metric"]
    return new ResultSummary(
        metricName,
        parameterNames,
        effectMeasureNames,
        rowsWithIdArray
    )
}

function resultJsonToWinnerLooserConfigResultSummary(json) {
    let usedMetricKey = "metric"
    let usedMetricName = json[usedMetricKey]
    let resultsKey = "results"
    let bestAndWorstConfigKey = "bestAndWorstConfigs"
    let bestConfigKey = "best"
    let worstConfigKey = "worst"
    let ids = Object.keys(json[resultsKey]).sort()
    let parameterNames = Object.keys(json[resultsKey][ids[0]][bestAndWorstConfigKey][bestConfigKey][0]).sort()
    let rowsWithIdArray = ids.map(id => {
        // configs are structured as {"param1": ["val1", "val2"], "param2": ["val1"],...}
        // thus taking keys gives the parameter names and the values for the keys
        // give the current (potentially multi-value) parameter setting
        let winnerConfig = json[resultsKey][id][bestAndWorstConfigKey][bestConfigKey][0]
        let winnerValue = json[resultsKey][id][bestAndWorstConfigKey][bestConfigKey][1]
        let looserConfig = json[resultsKey][id][bestAndWorstConfigKey][worstConfigKey][0]
        let looserValue = json[resultsKey][id][bestAndWorstConfigKey][worstConfigKey][1]

        console.debug(`winnerConfig: ${JSON.stringify(winnerConfig)}, looserConfig: ${JSON.stringify(looserConfig)}`)

        let parameterValuesWinnerConfig = parameterNames.map(name => formatValueArray(winnerConfig[name]))
        let parameterValuesLooserConfig = parameterNames.map(name => formatValueArray(looserConfig[name]))

        console.debug(`winnerParamConfig: ${JSON.stringify(parameterValuesWinnerConfig)}, looserParamConfig: ${JSON.stringify(parameterValuesLooserConfig)}`)

        // id is the tag here, values the parameter values and finally the metric value
        // we prepare two rows here per id, one for the best, other for the worst
        let winnerRow = [...parameterValuesWinnerConfig, numberAwareFormat(winnerValue, 4)]
        let looserRow = [...parameterValuesLooserConfig, numberAwareFormat(looserValue, 4)]
        return new RowsWithId(id, [winnerRow, looserRow])
    })
    return new ResultSummary(
        usedMetricName,
        [...parameterNames, "metric"],
        ["winner", "looser"],
        rowsWithIdArray
    )
}




export {
    RowsWithId,
    ResultSummary,
    resultJsonToEffectEstimateResultSummary,
    resultJsonToWinnerLooserConfigResultSummary
}
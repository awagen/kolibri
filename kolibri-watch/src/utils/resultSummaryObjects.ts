import {numberAwareComparison} from "./dataFunctions";

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

    // min and max values for the measures (e.g to give a percentage
    // of max that can be used for result coloring and so on)
    minMeasureValue: Number
    maxMeasureValue: Number

    // names of the value columns
    columnNames: Array<string>

    // available measures
    measureNames: Array<string>

    // every entry in the values array is assigned an id and
    // a number of value arrays. Here the row for measureName at index i
    // is to be found in the i-th row, while the value for columnName j
    // is found at the j-th value of the value row
    values: Array<RowsWithId>

    constructor(columnNames: Array<string>,
                measureNames: Array<string>,
                values: Array<RowsWithId>,
                minMeasureValue: Number = 0.0,
                maxMeasureValue: Number = 1.0) {
        this.columnNames = columnNames
        this.measureNames = measureNames
        this.values = values
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

    /**
     * Sort by measure and column identifiers.
     *
     *
     * @param measure
     * @param columnName
     * @param decreasing
     */
    sortByMeasureAndColumn(measure: string, columnName: string, decreasing: boolean): void {
        let measureIndex = this.measureNames.indexOf(measure)
        let columnIndex = this.columnNames.indexOf(columnName)
        if (measureIndex == undefined || columnIndex == undefined) {
            console.info(`Measure '${measure}' or columnName '${columnName}' undefined, aborting sort`)
            return
        }
        let sorted = [...this.values].sort(function(data1, data2) {
            let value1 = data1.getValue(measureIndex, columnIndex)
            let value2 = data2.getValue(measureIndex, columnIndex)
            return numberAwareComparison(value1, value2)
        })
        if (decreasing) sorted.reverse()
        this.values = sorted
    }

    toDict(): Object {
        return {
            columnNames: this.columnNames,
            measureNames: this.measureNames,
            values: this.values.map(value => value.toDict()),
            minMeasureValue: this.minMeasureValue,
            maxMeasureValue: this.maxMeasureValue
        }
    }

    fromDict(dict): ResultSummary {
        return new ResultSummary(
            dict["columnNames"],
            dict["measureNames"],
            dict["values"].map(value => RowsWithId.fromDict(value)),
            dict["minMeasureValue"],
            dict["maxMeasureValue"]
        )
    }



}


export {RowsWithId, ResultSummary}
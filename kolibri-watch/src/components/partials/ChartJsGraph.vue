<template>

  <div class="chartContainer">
    <canvas :id="canvasId"></canvas>
  </div>

</template>

<script>

import {Chart, registerables} from "chart.js";
import {Colors} from "chart.js"
import * as d3 from "d3";
import {interpolateYlGn} from "d3-scale-chromatic";
import _ from "lodash";
import {onMounted} from "vue";

Chart.register(...registerables);
Chart.register(Colors);

export default {

  name: "ChartJsGraph",
  props: {
    canvasId: {
      type: String,
      required: true
    },
    /**
     * each element in datasets needs to contain name and array of data points,
     * e.g {name: "n", data: [1.0,...]}.
     * Since labels need to be the same for all datasets, we pass them
     * only once instead of per dataset
     */
    datasets: {
      type: Array,
      required: true
    },
    labels: {
      type: Array,
      required: true
    },
    chartType: {
      type: String,
      required: false,
      default: "line"
    }
  },
  setup(props, context) {

    let numDatasets = props.datasets.length
    // data settings, enriching the passed info
    let data = {
      labels: props.labels,
      datasets: _.cloneDeep(props.datasets).map((data, index) => {
        let color = numDatasets > 1 ? d3.interpolateCool(index / (numDatasets - 1)) : interpolateYlGn(1.0)
        data["backgroundColor"] = color
        data["borderColor"] = color
        data["borderWidth"] = 1
        data["lineTension"] = 0.2
        data["fill"] = false
        return data
      })
    }
    // when using any of the below configs, make sure
    // to substitute the data field with above data object
    // line config
    let lineConfig = {
      type: 'line',
      data: {},
      options: {
        maintainAspectRatio: false,
        plugins: {
          colors: {
            enabled: false
          }
        }
      }
    };
    const barConfig = {
      type: 'bar',
      data: {},
      options: {
        maintainAspectRatio: false,
        plugins: {
          colors: {
            enabled: false
          }
        },
        indexAxis: 'x',
        scales: {
          xAxes: {
            ticks: {
              autoSkip: false,
              maxRotation: 90,
              minRotation: 90
            }
          }
        }
      }
    };


    let configType = {
      line: lineConfig,
      bar: barConfig
    }

    onMounted(() => {
      let chartElement = document.getElementById(props.canvasId)
      let chart = new Chart(
          chartElement,
          Object.assign({}, configType[props.chartType], {data: data})
      )
    })

    return {};

  }
}

</script>

<style scoped>

.chartContainer {
  height: 400px;
}

</style>
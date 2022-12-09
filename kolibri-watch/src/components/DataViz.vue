<template>

  <div class="row-container columns">
    <!--    <form class="form-horizontal col-6 column">-->
    <form class="form-horizontal col-12 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="timelineChart"></canvas>
          </div>
        </div>
      </div>
    </form>
    <!-- second placeholder for chart  -->
    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="timelineChart1"></canvas>
          </div>
        </div>
      </div>
    </form>

    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div class="timeSeriesContainer">
            <canvas id="barChart1"></canvas>
          </div>
        </div>
      </div>
    </form>

    <!--    <div class="form-horizontal col-12">-->
    <!--      <form class="form-horizontal col-6 column">-->
    <!--        <h3 class="k-title">-->
    <!--          Visualization Selection-->
    <!--        </h3>-->
    <!--      </form>-->
    <!--    </div>-->
  </div>

</template>

<script>
import Plotly from 'plotly.js-dist-min'
import {Chart, registerables} from "chart.js";
import {Colors} from "chart.js"

Chart.register(...registerables);
Chart.register(Colors);
import {onMounted} from "vue";
import dataVizTestJson from "../data/dataVizTestData.json";
import {preservingJsonFormat} from "../utils/formatFunctions";
import {interpolateYlGn} from "d3-scale-chromatic";
import * as d3 from 'd3';;

export default {

  components: {},
  props: [],
  methods: {},
  setup(props, context) {

    let numDatasets = dataVizTestJson["datasets"].length

    const data = {
      labels: dataVizTestJson["labels"],

      // for nice colors, see also:
      // https://medium.com/code-nebula/automatically-generate-chart-colors-with-chart-js-d3s-color-scales-f62e282b2b41
      // and
      // https://github.com/d3/d3-scale-chromatic
      datasets: dataVizTestJson["datasets"].map((data, index) => {
        let color = numDatasets > 1 ? d3.interpolateCool(index / (numDatasets - 1)) : interpolateYlGn(1.0)
        data["backgroundColor"] = color
        data["borderColor"] = color
        data["borderWidth"] = 1
        data["lineTension"] = 0.2
        data["fill"] = false
        return data
      })
    };

    console.info(`data: ${preservingJsonFormat(data)}`)

    const config = {
      type: 'line',
      data: data,
      options: {
        maintainAspectRatio: false,
        plugins: {
          colors: {
            enabled: false
          }
        }
      }
    };

    const barConfig1 = {
      type: 'bar',
      data: data,
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

    onMounted(() => {
      let chartElement = document.getElementById('timelineChart')
      let chartElement1 = document.getElementById('timelineChart1')
      let barChartElement1 = document.getElementById('barChart1')

      const chart = new Chart(
          chartElement,
          config
      );
      const chart1 = new Chart(
          chartElement1,
          config
      );
      const barChart1 = new Chart(
          barChartElement1,
          barConfig1
      );
    })

    return {
      Colors
    }


  }


}

</script>

<style scoped>

.row-container {
  margin: 3em;
}

.timeSeriesContainer {
  height: 400px;
}


</style>
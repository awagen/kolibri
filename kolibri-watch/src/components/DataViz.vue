<template>

  <div class="row-container columns">
    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div>
            <canvas id="timelineChart"></canvas>
          </div>
        </div>
      </div>
    </form>
    <form class="form-horizontal col-6 column">
      <div class="form-group">
        <div class="col-12 col-sm-12">
          <!-- chart.js element -->
          <div>
            <canvas id="timelineChart1"></canvas>
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
Chart.register(...registerables);
import {onMounted} from "vue";

export default {

  components: {},
  props: [],
  methods: {},
  setup(props, context) {

    const labels = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
    ];

    const data = {
      labels: labels,
      datasets: [{
        label: 'My First dataset',
        backgroundColor: 'rgb(255, 99, 132)',
        borderColor: 'rgb(255, 99, 132)',
        data: [0, 10, 5, 2, 20, 30, 45],
      }]
    };

    const config = {
      type: 'line',
      data: data,
      options: {}
    };

    onMounted(() => {
      let chartElement = document.getElementById('timelineChart')
      let chartElement1 = document.getElementById('timelineChart1')

      const chart = new Chart(
          chartElement,
          config
      );
      const chart1 = new Chart(
          chartElement1,
          config
      );
    })



  }

}

</script>

<style scoped>

.row-container {
  margin: 3em;
}



</style>
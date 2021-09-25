<template>
<!--  <svg id="dataviz_area" height=200 width=450>-->
<!--    <circle r="25" cx="100" cy="100" fill="purple;"></circle>-->
<!--  </svg>-->

  <table class="table">
    <thead>
    <tr>
      <th>job name</th>
      <th>type</th>
      <th>start date</th>
    </tr>
    </thead>
    <tbody>
    <tr>
      <td>Job1</td>
      <td>Parameter Optimization</td>
      <td>14 October 1994</td>
    </tr>
    </tbody>
  </table>

</template>

<script>
import * as d3 from "d3";
import {computed, onMounted} from "vue";

export default {
  props: {
    data: Array[String]
  },
  setup(props) {
    onMounted(() => {
      // let svg = d3.select("#dataviz_area");
      // svg.append("circle")
      //     .attr("cx", 120)
      //     .attr("cy", 120)
      //     .attr("r", 40)
      //     .style("fill", "blue");
      console.log("data: " + props.data.valueOf())
      console.log("first: " + computedValue.value)
    })

    const computedValue = computed(() => {
      return props.data[0];
    })

    return {}
  }

}

</script>

<style scoped>
#dataviz_area {
  width: 100%;
}

table {
  font-family: GlutenFont;
  font-size: medium;
  color: #9C9C9C;
  border-color: darkgrey;
}

tbody > tr {
  background-color: #313131;
}

th, td {
  border-color: black !important;
}

td, th {
  border-bottom: none !important;
}

</style>
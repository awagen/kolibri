<template>
  <!-- Create a div where the graph will take place -->
  <div id="my_dataviz"></div>
</template>

<style>
#my_dataviz {
  margin-top: 1em;
  justify-content: center;
  padding-left: 4em;
}

#my_dataviz svg {
  display: block;
  margin: auto;
}

text {
  pointer-events: none;
  font: 20px sans-serif;
}
</style>

<script>
import * as d3 from "d3";
import {onMounted, ref} from "vue";
import axios from "axios";

export default {
  props: {},
  setup(props) {
    const graph = ref(Object)

    onMounted(() => {
      // nodes
      const nodes = [
        {
          "type": "supervisor",
          "name": "node1"
        },
        {
          "type": "worker",
          "name": "node2"
        },
        {
          "type": "worker",
          "name": "node3"
        }
      ]

      let circle_radius_in_em = 1.2
      // append the svg object to the body of the page
      const svg = d3.select("#my_dataviz")
          .append("svg")
          .attr("height", "auto")

      const link1 = svg
          .append("line")
          .style("stroke", "#aaa")
          .style("stroke-dasharray", ("3,5")) // make the stroke dashed
          .attr("x1", `${circle_radius_in_em}em`)
          .attr("y1", `${circle_radius_in_em}em`)
          .attr("x2", `${circle_radius_in_em + 12}em`)
          .attr("y2", `${circle_radius_in_em}em`);

      const link2 = svg
          .append("line")
          .style("stroke", "#aaa")
          .style("stroke-dasharray", ("3,5")) // make the stroke dashed
          .attr("x1", `${circle_radius_in_em}em`)
          .attr("y1", `${circle_radius_in_em}em`)
          .attr("x2", `${circle_radius_in_em + 12}em`)
          .attr("y2", `${circle_radius_in_em + 4.0}em`);

      const supervisorGroup = svg
          .append("g")
      const supervisorNode = supervisorGroup
          .append("circle")
          .attr("r", `${circle_radius_in_em}em`)
          .style("fill", "#4E616D")
          .attr("cx", `${circle_radius_in_em}em`)
          .attr("cy", `${circle_radius_in_em}em`)
      // supervisorGroup
      //     .append("text")
      //     .text("S")
      //     .attr("dx", `${circle_radius_in_em / 2.0 + 0.1}em`)
      //     .attr("dy", `${circle_radius_in_em + 0.1}em`)

      const worker1Group = svg.append("g")
      const workerNode1 = worker1Group
          .append("circle")
          .attr("r", `${circle_radius_in_em}em`)
          .style("fill", "#596460")
          .attr("cx", `${circle_radius_in_em + 12}em`)
          .attr("cy", `${circle_radius_in_em}em`)
      // worker1Group
      //     .append("text")
      //     .text("W")
      //     .attr("dx", `${circle_radius_in_em + 8.9}em`)
      //     .attr("dy", `${circle_radius_in_em + 0.1}em`)

      const worker2Group = svg.append("g")
      const workerNode2 = worker2Group
          .append("circle")
          .attr("r", `${circle_radius_in_em}em`)
          .style("fill", "#596460")
          .attr("cx", `${circle_radius_in_em + 12}em`)
          .attr("cy", `${circle_radius_in_em + 4.0}em`)
      // worker2Group
      //     .append("text")
      //     .text("W")
      //     .attr("dx", `${circle_radius_in_em + 8.9}em`)
      //     .attr("dy", `${circle_radius_in_em + 3.3}em`)

    });
    return {}
  }
}

</script>
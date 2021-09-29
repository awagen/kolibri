<template>
  <!-- Create a div where the graph will take place -->
  <div id="my_dataviz"></div>
</template>

<style>
#my_dataviz {
  margin-top: 1em;
  justify-content: center;
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

      let circle_radius = 20
      // append the svg object to the body of the page
      const svg = d3.select("#my_dataviz")
          .append("svg")
          .attr("height", "auto")

      const link1 = svg
          .append("line")
          .style("stroke", "#aaa")
          .style("stroke-dasharray", ("3,5")) // make the stroke dashed
          .attr("x1", `${circle_radius}`)
          .attr("y1", `${circle_radius}`)
          .attr("x2", `${circle_radius + 200}`)
          .attr("y2", `${circle_radius}`);

      const link2 = svg
          .append("line")
          .style("stroke", "#aaa")
          .style("stroke-dasharray", ("3,5")) // make the stroke dashed
          .attr("x1", `${circle_radius}`)
          .attr("y1", `${circle_radius}`)
          .attr("x2", `${circle_radius + 200}`)
          .attr("y2", `${circle_radius + 50}`);

      const supervisorGroup = svg
          .append("g")
          .text("supervisor")
      const supervisorNode = supervisorGroup
          .append("circle")
          .attr("r", circle_radius)
          .style("fill", "#4E616D")
          .attr("cx", `${circle_radius}`)
          .attr("cy", `${circle_radius}`)
      supervisorGroup
          .append("text")
          .text("supervisor")
          .attr("dy", `${2 * circle_radius + 10}`)

      const workerNode1 = svg
          .append("circle")
          .attr("r", circle_radius)
          .style("fill", "#596460")
          .attr("cx", `${circle_radius + 200}`)
          .attr("cy", `${circle_radius}`)

      const workerNode2 = svg
          .append("circle")
          .attr("r", circle_radius)
          // .style("fill", "#69b3a2")
          .style("fill", "#596460")
          .attr("cx", `${circle_radius + 200}`)
          .attr("cy", `${circle_radius + 50}`)

    });
    return {}
  }
}

</script>
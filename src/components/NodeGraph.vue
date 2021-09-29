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

export default {
  props: {},
  setup() {
    function svgElementFromNodes(nodes) {
      console.info("calling svg element creation")
      console.log(nodes)
      let supervisor = nodes.filter(x => x["type"] === "supervisor")[0];
      console.log(supervisor)
      let workers = nodes.filter(x => x["type"] !== "supervisor");
      if (supervisor == null) {
        console.info("no supervisor node found")
        return ""
      }
      console.log(workers)

      // create the container svg
      let svg = d3.create("svg")
      // calculate positions of workers
      let circle_radius_in_em = 1.2
      let supervisor_coordinates_in_em = [circle_radius_in_em, circle_radius_in_em]
      let worker_start_x_in_em = circle_radius_in_em + 12
      let worker_start_y_in_em = circle_radius_in_em
      let worker_coordinates = []
      workers.forEach(_ => {
        worker_coordinates.push([worker_start_x_in_em, worker_start_y_in_em])
        worker_start_y_in_em += 4.0
      })
      console.log(worker_coordinates)

      worker_coordinates.forEach(worker_loc => {
        svg
            .append("line")
            .style("stroke", "#aaa")
            .style("stroke-dasharray", ("3,5")) // make the stroke dashed
            .attr("x1", `${supervisor_coordinates_in_em[0]}em`)
            .attr("y1", `${supervisor_coordinates_in_em[1]}em`)
            .attr("x2", `${worker_loc[0]}em`)
            .attr("y2", `${worker_loc[1]}em`);
      })

      // now place supervisor node (with group to be able to add text)
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

      worker_coordinates.forEach(worker_loc => {
        svg.append("g")
            .append("circle")
            .attr("r", `${circle_radius_in_em}em`)
            .style("fill", "#596460")
            .attr("cx", `${worker_loc[0]}em`)
            .attr("cy", `${worker_loc[1]}em`)
      })
      console.info("created svg")
      console.log(svg)
      console.info("svg html:" + svg.html())
      // hack around sizing of svg, which doesnt dynamically adjust height
      if (worker_coordinates.length > 0) {
        d3.select("#my_dataviz")
            .html(`<svg style="height:${worker_coordinates.length * 4}em">${svg.html()}</svg>`)
      }
      else {
        d3.select("#my_dataviz")
            .html(`<svg style="height:${3 * circle_radius_in_em}em">${svg.html()}</svg>`)
      }
      return svg
    }

    onMounted(() => {
      // test nodes
      let nodes = [
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
        },
        {
          "type": "worker",
          "name": "node4"
        },
        {
          "type": "worker",
          "name": "node4"
        },
        {
          "type": "worker",
          "name": "node4"
        },
        {
          "type": "worker",
          "name": "node4"
        }
      ];
      // TODO: instead of fixed element, need to schedule
      let refreshIntervalInMs = 5000
      // initial call
      svgElementFromNodes(nodes)
      // window.setInterval(() => {
      //   svgElementFromNodes(nodes)
      // }, refreshIntervalInMs)


    });
    return {}
  }
}

</script>
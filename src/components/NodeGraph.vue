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
  font: 14px sans-serif;
}
</style>

<script>
import * as d3 from "d3";
import {onMounted} from "vue";
import axios from "axios";
import { nodeStateUrl } from '../utils/globalConstants'

export default {
  props: {},
  setup() {
    const refreshIntervalInMs = 5000
    let nodeStates = []

    function retrieveNodeStatus() {
      console.log("executing retrieveNodeStatus")
      return axios
          .get(nodeStateUrl)
          .then(response => {
            let data = response.data
            // TODO: remove, must come from backend itself
            data[0]["type"] = "supervisor"
            data.slice(1,data.length).forEach(d => d["type"] = "worker")
            nodeStates = data
          }).catch(_ => {
            nodeStates = []
          })
          .finally(() => svgElementFromNodes(nodeStates))
    }

    function svgElementFromNodes(nodes) {
      let supervisor = nodes.filter(x => x["type"] === "supervisor")[0];
      let workers = nodes.filter(x => x["type"] !== "supervisor").map(worker => {
            let worker_state = {}
            worker_state["avgCpuUsage"] = (worker["cpuInfo"]["loadAvg"] / worker["cpuInfo"]["nrProcessors"]).toFixed(2) + "%"
            worker_state["heapUsage"] = (worker["heapInfo"]["heapUsed"] / worker["heapInfo"]["heapMax"]).toFixed(2) + "%"
            worker_state["host"] = worker["host"]
            worker_state["port"] = worker["port"]
            return worker_state
          });

      if (supervisor == null) {
        console.info("no supervisor node found")
        return ""
      }

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

      worker_coordinates.forEach(function (worker_loc, index) {
        let workerGroup = svg.append("g")
        workerGroup.append("circle")
            .attr("r", `${circle_radius_in_em}em`)
            .style("fill", "#596460")
            .attr("cx", `${worker_loc[0]}em`)
            .attr("cy", `${worker_loc[1]}em`)
        let load_info = workers[index]
        workerGroup
            .append("text")
            .text(JSON.stringify(load_info))
            .attr("dx", `${worker_loc[0] + 4}em`)
            .attr("dy", `${worker_loc[1] + 0.4}em`)
            .style("fill", "#9C9C9C")
      })
      // hack around sizing of svg, which doesnt dynamically adjust height
      if (worker_coordinates.length > 0) {
        d3.select("#my_dataviz")
            .html(`<svg style="height:${worker_coordinates.length * 4}em; width: 800px">${svg.html()}</svg>`)
      } else {
        d3.select("#my_dataviz")
            .html(`<svg style="height:${3 * circle_radius_in_em}em; width: 600px">${svg.html()}</svg>`)
      }
      return svg
    }

    onMounted(() => {
      // initial call
      retrieveNodeStatus()
      window.setInterval(() => {
        retrieveNodeStatus()
      }, refreshIntervalInMs)


    });
    return {}
  }
}

</script>
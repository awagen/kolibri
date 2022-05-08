<template>
  <!-- d3 works on svg elements, and here it is -->
  <svg>
  </svg>
</template>

<script>
import * as d3 from 'd3';
import _ from "lodash";

export default {

  // this component takes array in the shape [{id: "id1", value: 0.1}, {id: "id2", value: 0.03},...]
  props: ["idValueArray"],
  data() {
    return {
      chart: null
    };
  },
  watch: {
    idValueArray(value) {
      if (this.chart != null) this.chart.remove();
      this.renderChart(value);
    }
  },
  methods: {

    renderChart(idValueArray) {
      const margin = 60;
      const svg_width = 800;
      const svg_height = 600;
      const chart_width = svg_width - 2 * margin;
      const chart_height = svg_height - 2 * margin;

      const svg = d3
          .select("svg")
          .attr("width", svg_width)
          .attr("height", svg_height);

      this.chart = svg
          .append("g")
          .attr("transform", `translate(${margin}, ${margin})`);

      const yScale = d3
          .scaleLinear()
          .range([chart_height, 0])
          .domain([0, _.maxBy(idValueArray, "value").value]);

      this.chart
          .append("g")
          .call(d3.axisLeft(yScale));

      const xScale = d3
          .scaleBand()
          .range([0, chart_width])
          .domain(idValueArray.map(s => s.id))
          .padding(0.2);

      this.chart
          .append("g")
          .attr("transform", `translate(0, ${chart_height})`)
          .call(d3.axisBottom(xScale));

      const barGroups = this.chart
          .selectAll("rect")
          .data(idValueArray)
          .enter();

      barGroups
          .append("rect")
          .attr("class", "k-gbar")
          .attr("x", g => xScale(g.id))
          .attr("y", g => yScale(g.value))
          .attr("height", g => chart_height - yScale(g.value))
          .attr("width", xScale.bandwidth())

      // adding some text
      svg
          .append('text')
          .attr('class', 'label')
          .attr('x', -(chart_height / 2) - margin)
          .attr('y', margin / 4.8)
          .attr('transform', 'rotate(-90)')
          .attr('text-anchor', 'middle')
          .text('Values')

      svg
          .append('text')
          .attr('class', 'label')
          .attr('x', chart_width / 2 + margin)
          .attr('y', chart_height + margin * 1.7)
          .attr('text-anchor', 'middle')
          .text('ID')
    }

  }
}

</script>

<style scoped>

</style>
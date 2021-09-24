<template>
  <svg id="dataviz_area" height=200 width=450>
    <circle r="25" cx="100" cy="100" fill="purple;"></circle>
  </svg>
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
      let svg = d3.select("#dataviz_area");
      svg.append("circle")
          .attr("cx", 120)
          .attr("cy", 120)
          .attr("r", 40)
          .style("fill", "blue");
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
</style>
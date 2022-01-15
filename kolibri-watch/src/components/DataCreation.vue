<template>
  <!-- for below steps, show the selected data as json element that would reflect the created-by-drag-and-drop sample -->

  <!-- 1) single multivalues / generators, for single value types -->
  <!-- 2) bubbles to combine single generators to full permutations. Further, allow bubbles to contain bubbles, where each
   bubble represents its own permutation to allow subgroupings -->
  <!-- 3) api endpoint to generate sample n to m and base stats (num elements total, num single generators) -->
  <!-- 4) api endpoint for sample batch split -->
  <!-- 5) selection of permutation-transformations to check visually (e.g params, body, header,...) (e.g combine values into a modified request) -->
  <!-- 6) computation type selection (first restrict to the search evaluation use-base, can extend later) -->
  <!-- 7) job submission -->

  <!-- need creation field, and here: type (params, header, body), values: option to load values from file. In case of header and params make
    name definable. On generate click show the generated values and the json format of the data definition -->




</template>

<script>

import {onMounted} from "vue";

export default {

  props: [],
  methods: {

  },
  setup(props) {
    onMounted(() => {
    })
    return {}
  }

}

</script>

<style scoped>

</style>




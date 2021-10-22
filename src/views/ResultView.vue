<template>
  <pre>
  {{json}}
  </pre>
</template>

<script>
import * as d3 from "d3";
import {onMounted, ref} from "vue";

export default {

  components: {
  },
  props: [],
  setup(props) {
    const json = ref(JSON.stringify({"data": "val1", "p": "y"},null,'\t'))
    onMounted(() => {
    })

    return {
      json
    }
  }
}

</script>

<style scoped>

</style>

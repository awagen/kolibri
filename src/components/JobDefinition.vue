<template>
  <pre>
  {{json}}
  </pre>
</template>

<script>
import {onMounted, ref} from "vue";
import axios from "axios";

export default {

  props: [],
  setup(props) {
    const json = ref(JSON.stringify({"data": "val1", "p": "y"},null,'\t'))
    onMounted(() => {
    })

    return {
      json
    }
  },


}

</script>

<style scoped>

</style>

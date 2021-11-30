<template>
  <pre>
  {{ json }}
  </pre>
</template>

<script>
import {onMounted, ref} from "vue";

export default {

  components: {
  },
  props: [],
  setup(props) {
    const json = ref(JSON.stringify({"data": "val1", "p": "y"}, null, '\t'))
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

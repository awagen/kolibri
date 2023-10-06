<template>

  <ProductCarousel
    :show-description="false"
    :show-title="false"
    id="id1"
  />

</template>


<script>


import {defineComponent} from "vue";
import ProductCarousel from "@/components/partials/products/ProductCarousel.vue";

export default defineComponent({
  components: {ProductCarousel}
})
</script>


<style scoped>


</style>
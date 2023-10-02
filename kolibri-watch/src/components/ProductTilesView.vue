<template>

<!--  <div class="row-container columns">-->

    <ProductTile
        image-url="https://i.otto.de/i/otto/3cae2f65-543c-5ec1-be6c-3f88f4341ffe?$$tile_portrait$$&fmt=webp"
        title="Great product"
        description="Easy peasy lemon squeezy!"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/f03260dc-4735-500e-b2f4-8ecef542f012?$$tile_portrait$$&fmt=webp"
        title="Magnificent thing"
        description="Munch munch"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/9b2465fe-a566-5705-bcfb-3ee250d9a1e1?$$tile_portrait$$&fmt=webp"
        title="Best jeans the world has ever seen"
        description="Wohooo this rocks"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/6afeaec1-815c-530d-ab72-460bdc7e384e?$$tile_portrait$$&fmt=webp"
        title="Outch!"
        description="If one, this one!"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/a4bc5211-42a4-5e73-8a7e-e2ee391b37ab?$$tile_portrait$$&fmt=webp"
        title="Muhahahaha!"
        description="This, no other"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/cba8352a-4302-56f1-aeac-f290367103cc?$$tile_portrait$$&fmt=webp"
        title="Yummi!"
        description="Speedy thing that is!"
    />

<!--  </div>-->


</template>


<script>


import {defineComponent} from "vue";
import ProductTile from "@/components/partials/products/ProductTile.vue";

export default defineComponent({
  components: {ProductTile}
})
</script>


<style scoped>

.k-product-tile {

  float: left;

}

</style>
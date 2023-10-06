<template>

  <div :id="id" class="k-product-carousel">

    <ProductTile
        image-url="https://i.otto.de/i/otto/3cae2f65-543c-5ec1-be6c-3f88f4341ffe?$$tile_portrait$$&fmt=webp"
        title="Great product"
        description="Easy peasy lemon squeezy!"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item1"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/f03260dc-4735-500e-b2f4-8ecef542f012?$$tile_portrait$$&fmt=webp"
        title="Magnificent thing"
        description="Munch munch"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item2"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/9b2465fe-a566-5705-bcfb-3ee250d9a1e1?$$tile_portrait$$&fmt=webp"
        title="Best jeans the world has ever seen"
        description="Wohooo this rocks"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item3"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/6afeaec1-815c-530d-ab72-460bdc7e384e?$$tile_portrait$$&fmt=webp"
        title="Outch!"
        description="If one, this one!"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item4"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/a4bc5211-42a4-5e73-8a7e-e2ee391b37ab?$$tile_portrait$$&fmt=webp"
        title="Muhahahaha!"
        description="This, no other"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item5"
    />

    <ProductTile
        image-url="https://i.otto.de/i/otto/cba8352a-4302-56f1-aeac-f290367103cc?$$tile_portrait$$&fmt=webp"
        title="Yummi!"
        description="Speedy thing that is!"
        :show-description="showDescription"
        :show-title="showTitle"
        add-class="item6"
    />

  </div>


</template>


<script>

import ProductTile from "@/components/partials/products/ProductTile.vue";

export default {

  props: {
    id: {
      type: String,
      required: true
    },
    showDescription: {
      type: Boolean,
      required: false,
      default: true
    },
    showTitle: {
      type: Boolean,
      required: false,
      default: true
    },
    numProducts: {
      type: Number,
      required: false,
      default: 4
    }
  },
  components: {ProductTile},
  computed: {
  },
  setup(props, context) {
    return {}
  }
}

</script>


<style scoped>

.k-product-tile {

  text-align: left;
  width: 5em;
  border: 1px solid #353535 !important;

}

.k-product-carousel {

  display: grid;
  column-gap: 0.3em;
  row-gap: 0.2em;
  float: left;
  box-sizing: border-box;
  margin: 1.0em;

}

.item1 {
  grid-column-start: 1;
  grid-column-end: 2;
}

.item2 {
  grid-column-start: 2;
  grid-column-end: 3;
}

.item3 {
  grid-column-start: 3;
  grid-column-end: 4;
}

.item4 {
  grid-column-start: 4;
  grid-column-end: 5;
}

.item5 {
  grid-column-start: 5;
  grid-column-end: 6;
}

.item6 {
  grid-column-start: 6;
  grid-column-end: 7;
}

</style>
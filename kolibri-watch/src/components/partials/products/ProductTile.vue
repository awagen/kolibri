<template>

  <div class="k-product-tile">

    <div class="k-product-title-container">

      <span>{{title}}</span>

    </div>

    <div class="k-product-desc-container">

      <span>{{description}}</span>

    </div>

    <div class="k-product-image-container">

      <img class="k-product-image" :src="imageUrl" alt=""/>

    </div>

  </div>


</template>


<script>

export default {

  props: {

    title: {
      type: String,
      required: true,
      description: "Title of the product."
    },
    description: {
      type: String,
      required: false,
      description: "Description of the product."
    },
    imageUrl: {
      type: String,
      required: false,
      description: "Url to retrieve the produce image from."
    }

  },

  setup(props, context) {

    return {}

  }

}

</script>


<style scoped>

.k-product-tile {

  max-width: 10em;
  margin: 1em;

  border: 1px solid #353535 !important;

}

.k-product-title-container {

  font-weight: bold;
  font-size: large;

  height: 1em;
  line-height: 1em;

  /*
  white-space: nowrap;
  */
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  display: -webkit-box;

  margin-bottom: 0.5em;

}

.k-product-desc-container {

  font-size: medium;
  /* white-space: nowrap; */

  height: 2em;
  line-height: 1em;

  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  display: -webkit-box;

  margin-bottom: 0.5em;

}

img {

  display: block;
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  margin-left: auto;
  margin-right: auto;

}


</style>
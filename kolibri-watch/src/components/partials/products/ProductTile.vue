<template>

  <div :class="'k-product-tile' + ' ' + addClass">

    <div v-if="showTitle && showDescription" class="k-product-title-container" :style="{'height': 1 + 'em', '-webkit-line-clamp': 1, 'font-size': 'large', 'font-weight': 'bold'}">

      <span>{{title}}</span>

    </div>

    <div v-else-if="showTitle" class="k-product-title-container" :style="{'height': 2 + 'em', '-webkit-line-clamp': 2, 'font-size': 'medium'}">

      <span>{{title}}</span>

    </div>

    <div v-if="showDescription" class="k-product-desc-container">

      <span>{{description}}</span>

    </div>

    <div class="k-product-image-container">

      <Popover
          cart-body="Some description"
          popover-position="right"
          add-button-class="layered"
      />

      <img class="k-product-image layered" :src="imageUrl" alt=""/>

    </div>

  </div>


</template>


<script>

import Popover from "@/components/partials/Popover.vue";

export default {
  components: {Popover},

  props: {

    addClass: {
      type: String,
      required: false,
      default: ""
    },
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
    showTitle: {
      type: Boolean,
      required: false,
      default: true
    },
    showDescription: {
      type: Boolean,
      required: false,
      default: true
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

  height: auto;

}

.k-product-title-container {

  /* setting height of text line so we can limit the height to this times the amount of rows
   wed like */
  line-height: 1em;
  overflow: hidden;

  /* Some base properties to enable the ending of text extending the available rows with ...
   (the additional properties are set inline to take passed props into account)
   */
  -webkit-box-orient: vertical;
  display: -webkit-box;

  margin-bottom: 0.2em;

}

.k-product-desc-container {

  font-size: medium;

  /* make room for two rows of text */
  height: 2em;
  line-height: 1em;
  overflow: hidden;

  /* End text extending beyond two rows with ... */
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  display: -webkit-box;

  margin-bottom: 0.2em;

}


img {

  display: block;
  max-width: 100%;
  max-height: 100%;
  margin-left: auto;
  margin-right: auto;

}


.k-product-image-container {

  margin-left: auto;
  margin-right: auto;

  /* playing with properties to try to layer both the image and the popover over each other
   Can remove if not successful*/
  display: grid;
  position: relative;

}

/* all elements within an element where display: grid is set and that have the layered class
 are stacked on top of each other */
.layered > * {
  grid-column-start: 1;
  grid-row-start: 1;
}


</style>
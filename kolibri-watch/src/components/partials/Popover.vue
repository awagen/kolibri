<template>

  <div
      :id="popoverId !== '' ? popoverId : null"
      :class="{
    'popover': true,
    'popover-right': popoverPosition === 'right',
    'popover-bottom': popoverPosition === 'bottom',
    'popover-left': popoverPosition === 'left',
    'k-button-fill-container': buttonFillSurroundingElement
  }">

    <!-- element to cause popover -->
    <button
        :class="'btn btn-action' + ' ' + addButtonClass + ' ' + (buttonVisible ? '' : 'k-button-invisible')">
      {{ buttonContent }}
    </button>

    <!-- the actual popover -->
    <div class="popover-container">
      <div class="card">
        <div class="card-header">
          {{ cartHeader }}
        </div>
        <div class="card-body">
          {{ cartBody }}
        </div>
        <div class="card-footer">
          {{ cartFooter }}
        </div>
      </div>
    </div>
  </div>

</template>


<script>

export default {

  props: {

    popoverId: {
      type: String,
      required: false,
      default: ""
    },
    buttonVisible: {
      type: Boolean,
      required: false,
      default: false
    },
    buttonFillSurroundingElement: {
      type: Boolean,
      required: false,
      default: true
    },
    addButtonClass: {
      type: String,
      required: false,
      description: "Class to add to the button causing the popover",
      default: ""
    },
    buttonContent: {
      type: String,
      required: false,
      default: ""
    },
    popoverPosition: {
      type: String,
      required: false,
      default: "",
      validator: function (value) {
        return ["right", "bottom", "left", ""].includes(value);
      }
    },
    cartHeader: {
      type: String,
      required: false,
      default: ""
    },
    cartBody: {
      type: String,
      required: false,
      default: ""
    },
    cartFooter: {
      type: String,
      required: false,
      default: ""
    }

  },

  setup(props, context) {
    return {}
  }

}


</script>


<style scoped>

.k-button-invisible {

  background: transparent;
  border: none !important;
  font-size: 0;
  display: none;

}

.k-button-fill-container {

  position: absolute;
  width: 100%;
  height: 100%;
  max-height: 100%;

}


.popover-container {
  position: relative;
  top: 0;
  left: 0;
  max-width: 10em !important;
  max-height: 2em !important;
}


</style>
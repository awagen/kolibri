<template>

  <div :class="mode" class="modal modal-sm" v-bind:class="{'active':(show)}" id="modal-id">
    <a href="#close" @click="close()" class="modal-overlay" aria-label="Close"></a>
    <!-- progress bar element and the OK / FAIL sign for the animation  -->
    <div class="k-anime-ok-ball"></div>
    <svg v-if="mode === 'k-success'" id="k-modal-svg" viewBox="0 0 25 30">
      <path class="k-anime-check st0" d="M2,19.2C5.9,23.6,9.4,28,9.4,28L23,2"/>-
    </svg>
    <svg v-if="mode === 'k-fail'" id="k-modal-svg" viewBox="0 0 40 40">
      <path class="k-anime-check st0" d="M 5 5 L 35 35 Z M 5 35 L 35 5 Z" />
    </svg>

    <!-- So far we only display any further content if its a fail-case  -->
    <div v-if="mode === 'k-fail'" class="modal-container">
      <div v-if="modalTitle !== undefined && modalTitle !== ''" class="modal-header">
<!--        <a href="#close" @click="close()" class="btn btn-clear float-right" aria-label="Close"></a>-->
        <div class="modal-title h5">{{modalTitle}}</div>
      </div>
      <div v-if="mainContent !== undefined && mainContent !== ''" class="modal-body">
        <div class="content">
          {{ mainContent }}
        </div>
      </div>
      <div v-if="footerContent !== undefined && footerContent !== ''" class="modal-footer">
        {{footerContent}}
      </div>
    </div>

  </div>


</template>

<script>

import anime from 'animejs/lib/anime.es.js';
import {watch} from "vue";

export default {

  props: {
    show: {
      type: Boolean,
      required: true,
      default: false,
    },
    mode: {
      type: String,
      required: false,
      default: "k-success",
      validator: function(value) {
        return ["k-success", "k-fail"].includes(value);
      }
    },
    fadeOutOk: {
      type: Boolean,
      required: false,
      default: true
    },
    modalTitle: {
      type: String,
      required: false,
      default: ""
    },
    mainContent: {
      type: String,
      required: false,
      default: ""
    },
    footerContent: {
      type: String,
      required: false,
      default: ""
    }
  },
  components: {},
  methods: {},
  emits: ["responseModalClosed", "responseModalOpened"],
  setup(props, context) {

    function open() {
      context.emit("responseModalOpened")
    }

    function close() {
      context.emit("responseModalClosed")
    }

    watch(() => props.show, (newValue, oldValue) => {
      if (newValue) {
        getOKPlayable().play()
      }
    })

    /**
     * Retrieve the object representing the animation.
     * Call el.play() will run the animation
     * @returns The anime.timeline object on which we can call play to run the animation
     */
    function getOKPlayable() {
      let basicTimeline = anime.timeline({
        autoplay: false
      });

      let bubbleColor = props.mode === 'k-success' ? "#71DFBE" : "#7E0000"

      basicTimeline
          .add({
            targets: ".k-anime-check",
            duration: 1,
            opacity: 0
          })
          .add({
            targets: ".k-anime-ok-ball",
            width: 0,
            height: 10,
            duration: 1,
            borderRadius: 200,
            backgroundColor: "#2B2D2F"
          })
          .add({
            targets: ".k-anime-check",
            duration: 1,
            opacity: 1
          })
          .add({
            targets: ".k-anime-ok-ball",
            width: 320,
            height: 320,
            delay: 100,
            duration: 800,
            borderRadius: 160,
            backgroundColor: `${bubbleColor}`
          })
      if (props.mode === 'k-success' && props.fadeOutOk) {
        basicTimeline.add({
          targets: "modal",
          update: function () {
            close()
          }
        })
      }

      return basicTimeline;

    }

    return {
      open,
      close
    }
  }

}
</script>

<style scoped>

.modal {
  color: #303742;
}

.modal .modal-overlay {
  background: rgba(247, 248, 249, .01) !important;
}

.modal.k-success.modal-sm .modal-container {
  border: 0.4em solid darkgreen !important;
  background-color: #588274;
}

.modal.k-fail.modal-sm .modal-container {
  border: 0.4em solid darkred !important;
  background-color: lightcoral;
}

/** specifics for the animation as inspired by https://codepen.io/andrewmillen/pen/MoKLob  **/
.k-anime-ok-ball {
  position: absolute;
  height: 10px;
  width: 0;
  right: 0;
  top: 50%;
  left: 50%;
  border-radius: 200px;
  transform: translateY(-50%) translateX(-50%);
  background: #2B2D2F;
}

/** svg container **/
svg {
  width: 120px;
  position: absolute;
  top: 50%;
  transform: translateY(-50%) translateX(-50%);
  left: 50%;
  right: 0;
}

/** element for the ok-stroke **/
.k-anime-check {
  fill: none;
  stroke: #FFFFFF;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}


</style>
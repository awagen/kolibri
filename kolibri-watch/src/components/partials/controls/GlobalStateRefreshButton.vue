<template>

  <div class="k-globalStateRefreshButtonContainer">
    <button @click.prevent="handleClick"
            :class="'btn btn-primary s-circle k-refresh'">
      <i class="icon icon-download"></i>
    </button>
  </div>

</template>

<script>

import {useStore} from "vuex";

export default {

  props: {
    emittedEventNames: {
      type: Array,
      required: true,
      description: "Name of events to be emitted to vuex store on click." +
          "Note that they take no parameters. Events are emitted in " +
          "the passed order."
    }
  },

  setup(props, context) {

    let store = useStore()

    function handleClick() {
      props.emittedEventNames.forEach(eventName => {
        store.commit(eventName)
      })
    }

    return  {handleClick}

  }

}



</script>


<style>

button.k-refresh {
  background-color: darkslategray !important;
  border-width: 0;
}

button.k-refresh:hover,button.k-refresh:focus {
  background-color: darkgray !important;
}

.k-globalStateRefreshButtonContainer {
  text-align: right;
  margin-right: 2em;
}

</style>
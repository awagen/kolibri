<template>

  <!-- json overview container -->
  <template v-if="currentJson !== undefined">
    <form class="form-horizontal column">

      <h3 class="k-title">
        {{ title }}
      </h3>

      <div class="k-json-container col-12 col-sm-12">
        <pre class="template-content-display" v-html="currentJson"/>
      </div>

    </form>
  </template>

</template>


<script>

import {objectToJsonStringAndSyntaxHighlight} from "@/utils/formatFunctions";

export default  {

  props: {
    title: {
      type: String,
      required: false,
      default: ""
    },
    currentJson: {
      type: String,
      required: false,
      default: objectToJsonStringAndSyntaxHighlight({})
    },
  },
  setup() {
    return {}
  }

}

</script>

<style scoped>

pre.template-content-display {
  margin-top: 2em;
}

.form-horizontal {
  padding: .4rem 0;
}

.k-json-container {
  overflow: scroll;
}

.k-title {
  font-size: x-large;
}

/* need some deep selectors here since otherwise code loaded in v-html directive doesnt get styled */
::v-deep(pre) {
  padding-left: 2em;
  padding-top: 0;
  margin: 5px;
  text-align: left;
}

::v-deep(.string) {
  color: green;
}

::v-deep(.number) {
  color: darkorange;
}

::v-deep(.boolean) {
  color: black;
}

::v-deep(.null) {
  color: magenta;
}

::v-deep(.key) {
  color: #9c9c9c;
}



</style>
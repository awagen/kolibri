<template>

  <div class="row-container columns">
    <ul class="tab tab-block">
      <li v-for="(dataValues, dataType, _) in fileDataByTypeMapping" class="tab-item"
          v-bind:class="{'active':(selectedDataFileType === dataType)}">
        <a href="#" @click.prevent="this.$emit('fileTypeUpdateCall', dataType)" class="badge" :data-badge="dataValues.length">
          <span class="choice-title">{{ dataType }}</span>
        </a>
      </li>
    </ul>
  </div>

</template>

<script>

import {onMounted} from "vue";

export default {
  props: ["fileDataByTypeMapping", "selectedDataFileType"],
  methods: {},
  setup(props) {
    onMounted(() => {
    })
    return {}
  }
}

</script>


<style scoped>

.row-container {
  margin: 3em;
}

.badge {
  margin: 1em;
}

.badge[data-badge]::after {
  background: #25333C;
  box-shadow: 0 0 0 0.1rem #588274;
  color: lightgrey;
  margin-left: 0.5em;
}

.tab a .choice-title {
  /* need block style such that only uppercasing first char works */
  display: inline-block;
  text-transform: lowercase;
  color: #9C9C9C;
  margin-right: 1.5em;
}

.tab {
  border-bottom: none;
}

.tab li {
  margin-right: 2em;
  border-bottom: .05rem solid grey;
}

.tab li.active {
  border-bottom: .1rem solid white;
}

.tab.tab-block .tab-item {
  flex: 1 0 auto;
}

.tab-item.active .choice-title {
  color: white;
}

.tab-item.active a {
  border-bottom: none;
}

.tab a .choice-title::first-letter {
  text-transform: uppercase;
}

</style>
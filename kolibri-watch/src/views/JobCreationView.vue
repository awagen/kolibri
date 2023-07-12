<template>

  <div class="row-container columns">
    <ul class="tab tab-block">
      <li class="tab-item" v-bind:class="{'active':(isActiveInputDefView)}">
        <a href="#" @click.prevent="this.selectDisplay('inputDefinition')">
          <span class="choice-title">Form</span>
        </a>
      </li>
      <li class="tab-item" v-bind:class="{'active':(isActiveTemplateView)}">
        <a href="#" @click.prevent="this.selectDisplay('template')">
          <span class="choice-title">Raw</span>
        </a>
      </li>
<!--      <li class="tab-item" v-bind:class="{'active':(isActiveCreationView)}">-->
<!--        <a href="#" @click.prevent="this.selectDisplay('creation')">-->
<!--          <span class="choice-title">Parameter Composer</span>-->
<!--        </a>-->
<!--      </li>-->
    </ul>
  </div>

  <template v-if="this.isActiveInputDefView">
    <InputJobMsgView/>
  </template>
  <template v-if="this.isActiveTemplateView">
    <JobDefinitionByTemplates/>
  </template>
<!--  <template v-if="this.isActiveCreationView">-->
<!--    <DataCreation/>-->
<!--  </template>-->

</template>

<script>
import JobDefinitionByTemplates from "../components/JobDefinitionByTemplates.vue";
import DataCreation from "../components/DataCreation.vue";
import InputJobMsgView from "./InputJobMsgView.vue";

export default {

  components: {
    InputJobMsgView,
    JobDefinitionByTemplates,
    DataCreation
  },
  methods: {
    selectDisplay(type) {
      if (type === "inputDefinition"){
        this.isActiveInputDefView = true
        this.isActiveTemplateView = false
        this.isActiveCreationView = false
      }
      if (type === "template"){
        this.isActiveInputDefView = false
        this.isActiveTemplateView = true
        this.isActiveCreationView = false
      }
      else if (type === "creation"){
        this.isActiveInputDefView = false
        this.isActiveTemplateView = false
        this.isActiveCreationView = true
      }
    }
  },
  data() {
    return {
      isActiveInputDefView: true,
      isActiveTemplateView: false,
      isActiveCreationView: false
    }
  },
  setup() {
    return {}
  }
}
</script>

<style scoped>

.row-container {
  margin: 3em;
}

.tab a .choice-title {
  display: inline-block;
  text-transform: uppercase;
  color: #9C9C9C;
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
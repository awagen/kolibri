<template>

  <div class="row-container columns">
    <ul class="tab tab-block">
      <li>
        <a href="#" @click="this.selectDisplay('template')">
          <span class="choice-title">Templates</span>
        </a>
      </li>
      <li>
        <a href="#" @click="this.selectDisplay('creation')">
          <span class="choice-title">Parameter Composer</span>
        </a>
      </li>
    </ul>
  </div>

  <template v-if="this.isActiveTemplateView">
    <JobDefinitionByTemplates/>
  </template>
  <template v-if="this.isActiveCreationView">
    <DataCreation/>
  </template>

</template>

<script>
import JobDefinitionByTemplates from "../components/JobDefinitionByTemplates.vue";
import DataCreation from "../components/DataCreation.vue";

export default {

  components: {
    JobDefinitionByTemplates,
    DataCreation
  },
  methods: {
    selectDisplay(type) {
      if (type === "template"){
        console.info("template type")
        this.isActiveTemplateView = true
        this.isActiveCreationView = false
      }
      else if (type === "creation"){
        console.info("creation type")
        this.isActiveTemplateView = false
        this.isActiveCreationView = true
      }
    }
  },
  data() {
    return {
      isActiveTemplateView: true,
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
  margin-right: 1.5em;
}

.tab li {
  margin-right: 2em;
}

.tab-item.active .choice-title {
  color: black;
}

.tab-item.active a {
  border-bottom: none;
}

.tab a .choice-title::first-letter {
  text-transform: uppercase;
}

</style>
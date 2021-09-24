<template>
  <header class="navbar">
    <section class="navbar-section">
      <a href="#" class="btn btn-link">Docs</a>
      <a href="#" class="btn btn-link">Examples</a>
    </section>
    <section class="navbar-center">
      <!-- centered logo or brand -->
      <img id="menu-logo" alt="Kolibri logo" src="./assets/images/kolibri.svg"/>
    </section>
    <section class="navbar-section">
      <a href="#" class="btn btn-link">Twitter</a>
      <a href="#" class="btn btn-link">GitHub</a>
    </section>
  </header>

  <!-- divider element -->
  <div class="divider"></div>

  <KolibriHome :data="data" msg="Hello from Kolibri"/>
</template>

<script>
import {ref} from 'vue';
import KolibriHome from './components/KolibriHome.vue'

export default {

  components: {
    KolibriHome
  },
  setup() {
    const data = ref([10, 40, 15, 25, 50])
    return {
      data
    }
  }
}
</script>

<style scoped>
#menu-logo {
  width: 100px;
  margin-left: 10px;
}

.navbar {
  padding-top: 10px;
  padding-left: 100px;
  padding-right: 100px;
}

header {
  font-family: GlutenFont;
}

.btn.btn-link {

  color: darkblue;
  font-size: x-large;

}

</style>
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

  <KolibriHome :data="data"/>
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
  width: 7em;
  filter: invert(40%) sepia(3%) saturate(2476%) hue-rotate(86deg) brightness(118%) contrast(119%);
}

.navbar {
  padding: 1em 8em 0.2em;
}

header {
  font-family: GlutenFont;
}

.btn.btn-link {
  color: #9C9C9C;
  font-size: x-large;

}

.divider {
  border-color: #353535;
}

</style>
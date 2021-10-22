<template xmlns="http://www.w3.org/1999/html">
  <header class="navbar">
    <section class="navbar-section">
      <router-link class="btn btn-link" to="/">STATUS</router-link>
      <router-link class="btn btn-link" to="/history">HISTORY</router-link>
      <router-link class="btn btn-link" to="/jobCreation">CREATE</router-link>
    </section>
    <section class="navbar-center">
      <!-- centered logo or brand -->
      <img id="menu-logo" alt="Kolibri logo" src="./assets/images/kolibri.svg"/>
    </section>
    <section class="navbar-section">
      <router-link class="btn btn-link" to="/analyze">ANALYZE</router-link>
      <router-link class="btn btn-link" to="/result">RESULT</router-link>
    </section>
  </header>

  <!-- divider element -->
  <div class="divider"></div>
  <ServiceStatus/>

  <!-- within router-view the view selected in the nav will be rendered -->
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="in-out">
      <div>
        <component :is="Component"/>
      </div>
    </transition>
  </router-view>

</template>

<script>
import ServiceStatus from "./components/ServiceStatus.vue";

export default {

  components: {ServiceStatus},
  data() {
    return {}
  },
  setup() {
    return {}
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

.btn.btn-link {
  color: #9C9C9C;
  font-size: x-large;
  border: none !important;
  outline-style: none !important;
}

.btn-link:hover, .btn-link:active {
  color: black !important;
}

a:visited, a:focus {
  color: #9C9C9C !important;
}

.navbar-section a {
  padding-right: 2em;
}

.divider {
  border-color: #353535;
}

</style>
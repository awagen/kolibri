<template>
  <div class="viewContainer">

    <!-- Button to reload global state relevant for the selection -->
    <GlobalStateRefreshButton
        :emitted-event-names="[
            'updateResultDateToJobIdsMapping'
            ]"
    />

<!--  <DataAnalysisOverview></DataAnalysisOverview>-->
  <DataViz></DataViz>

  </div>
</template>

<script>
import DataAnalysisOverview from "../components/DataAnalysisOverview.vue";
import DataViz from "../components/DataViz.vue";
import GlobalStateRefreshButton from "@/components/partials/controls/GlobalStateRefreshButton.vue";


export default {

  components: {
    GlobalStateRefreshButton,
    DataViz,
    DataAnalysisOverview
  },
  data() {
    return {}
  },
  props: [],
  setup(props) {

    return {}
  },


}

</script>

<style scoped>

</style>

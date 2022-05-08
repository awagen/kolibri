<template>
  <div class="k-table-wrapper">
    <table class="table">
      <thead>
      <tr>
        <th>nr</th>
        <th v-for="column in columnHeaders">{{ column }}</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="(row, index) in data">
        <td>{{index}}</td>
        <td v-for="column in row">{{ column }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script>

export default {
  /**
   * columnHeaders: Assumes array of header names
   * data: Assumes array of arrays, where each single array holds the column values
   * corresponding to the header for the respective array-index
   */
  props: ["columnHeaders", "data"]

}

</script>



<style scoped>

.k-table-wrapper {
  display: inline-block;
  height: auto;
  max-height:60em;
  overflow: auto;
  width:100%;
}

/* keeps the header at the top while scrolling */
table thead {
  position: -webkit-sticky;
  position: sticky;
  top: 0;
  background-color: #25333C;
}

table {
  border-collapse: collapse;
  width: 100%;
}

</style>
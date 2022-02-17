<template>
  <table class="table">
    <thead>
    <tr>
      <th v-for="column in columnHeaders">{{ column }}</th>
    </tr>
    </thead>
    <tbody>
    <tr v-for="row in data">
      <td v-for="column in row">{{ column }}</td>
    </tr>
    </tbody>
  </table>
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

.table {
  font-size: .6rem;
  margin-left: 2em;
}

</style>
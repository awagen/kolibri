<template>
  <pre>
    <div class="table-container">
      <table class="table table-striped table-hover table-scroll">
        <thead>
        <tr>
          <th v-for="column in column_names">
            {{column}}
          </th>
        </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows">
            <td v-for="column in column_names">{{row[column]}}</td>
          </tr>
        </tbody>
      </table>

    </div>
  </pre>
</template>

<script>
import * as d3 from "d3";
import {onMounted, ref} from "vue";

export default {

  components: {},
  props: [],
  setup(props) {
    const column_names = ref(["a1",	"k1",	"k2", "o", "value-DCG_10", "value-NDCG_10", "value-ERR_10", "value-PRECISION_4"])
    const rows = ref([])

    async function d3LoadCsv(file) {
      return await d3.dsv("\t", file)
    }

    onMounted(() => {
      d3LoadCsv("/src/data/(ALL1)").then(r => {
        console.log(r)
        rows.value = r
      });
    })

    return {
      column_names,
      rows
    }
  }
}

</script>

<style scoped>

.table.table-striped tbody tr:nth-of-type(2n+1) {
  background: #25333C;
}

.table.table-striped tbody tr:nth-of-type(2n) {
  background: #25333C;
}

</style>

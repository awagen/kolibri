<template>
  <input id="int-input" class="form-input metric" type="number" step=1 :value="value" @input="updateValueEvent"
         placeholder="Number Input">
  <div id="k-msg-toast" class="toast toast-warning display-none">
    <button type='button' class="btn btn-clear float-right" @click="hideModal"></button>
    <span id="k-msg-toast-content"></span>
  </div>
</template>

<script>
import {ref} from "vue";

export default {

  props: {
    "min": Number,
    "max": Number,
    "name": String
  },
  components: {},
  methods: {
  },
  setup(props, context) {
    let minValue = (props.min != null) ? props.min : 0
    let value = ref(0)

    function validate(val) {
      // nothing entered yet or clearing
      if (val === "") {
        return true
      } else if (props.min !== undefined && val < props.min) {
        return false
      } else if (props.max !== undefined && val > props.max) {
        return false
      }
      return true
    }

    document.addEventListener('change', function handleClickOutsideBox(event) {
      hideModal()
    });

    function updateValueEvent(valueEvent) {
      console.debug("updated event called with value: " + valueEvent.target.value)
      hideModal()
      if (validate(valueEvent.target.value)) {
        value.value = valueEvent.target.value
        // emitting value to communicate to parent name and value
        // of the property.
        // if we traverse this for each element, we can build up all
        // substructures and communicate the changes downstream
        // upstream for the final result
        context.emit('valueChanged', {name: props.name, value: value.value})
      } else {
        document.getElementById('int-input').value = value.value;
        showModalMsg("size out of range")
        console.debug("value invalid, no update")
      }
    }

    function showModalMsg(msg) {
      document.getElementById('k-msg-toast-content').textContent = msg;
      document.getElementById('k-msg-toast').classList.remove("display-none");
    }

    function hideModal() {
      console.debug("hiding")
      document.getElementById('k-msg-toast').classList.add("display-none");
      document.getElementById('k-msg-toast-content').textContent = "";
    }

    return {
      // value,
      updateValueEvent,
      hideModal
    }
  }

}

</script>

<style scoped>

.form-input.metric {
  display: inline-block;
}

.display-none {
  display: none;
}

</style>



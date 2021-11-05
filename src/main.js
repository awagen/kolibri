import { createApp } from 'vue'
import App from './App.vue'
import './index.css'
import router from './router'

// we could just reference style sheets relatively from assets folder, but we keep one central scss file instead
// as central place, mixing sheets and overwriting styles
import './assets/css/styles.scss';

const app = createApp(App);
app.use(router)
app.mount('#app');

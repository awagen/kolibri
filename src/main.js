import { createApp } from 'vue'
import App from './App.vue'
import './index.css'

import "./assets/css/spectre-0.5.9/dist/spectre.min.css";
import "./assets/css/spectre-0.5.9/dist/spectre-exp.min.css";
import "./assets/css/spectre-0.5.9/dist/spectre-icons.min.css";

const app = createApp(App);
app.mount('#app');

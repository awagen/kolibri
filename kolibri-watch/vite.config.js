import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const path = require("path");
// https://vitejs.dev/config/
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            // to make vue '@' import alias work
            "@": path.resolve(__dirname, "./src"),
        },
    }
})
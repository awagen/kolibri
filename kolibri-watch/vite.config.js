import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const path = require("path");
// https://vitejs.dev/config/
export default defineConfig({
    plugins: [vue()],
    build: {
        sourcemap: true
    },
    resolve: {
        alias: {
            // to make vue '@' import alias work
            "@": path.resolve(__dirname, "./src"),
        },
    }
})
// vite is exposing env variables via import.meta.env (https://vitejs.dev/guide/env-and-mode.html#env-files)
// also there are some restrictions as to which env vars are picked up, e.g some specific values and all
// values prefixed with VITE_.
// In plain vue.js the prefix and the object to retrieve them by differ to the above (https://cli.vuejs.org/guide/mode-and-env.html#example-staging-mode),
// e.g VUE_APP_ prefix and process.env object to retrieve the values from
export const kolibriBaseUrl = import.meta.env.VITE_KOLIBRI_BASE_URL
export const appIsUpUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_HEALTH_PATH}`
export const nodeStateUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_NODE_STATE_PATH}`
export const jobStateUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_JOB_STATES_PATH}`
export const jobBatchStateUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_BATCH_STATE_PATH}`
export const jobHistoryUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_JOB_HISTORY_PATH}`
export const stopJobUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_STOP_JOB_PATH}`
export const kolibriStateRefreshInterval = parseInt(import.meta.env.VITE_KOLIBRI_STATE_RETRIEVAL_REFRESH_INTERVAL_IN_MS)

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

// url to retrieve available data files by type
export const dataFileInfoAllUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_DATA_INFO_ALL_PATH}`

export const parameterValuesSampleRequestUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_DATA_REQUEST_SAMPLE_PATH}`

// urls to retrieve some result information
export const resultExecutionIdsOverviewUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_OVERVIEW_PATH}`
export const resultExecutionIdsSingleResultsOverviewUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_SINGLE_RESULT_OVERVIEW_PATH}`
export const resultExecutionIdGetDataByIdUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_GET_DATA_PATH}`
export const resultExecutionIdGetFilteredDataByIdUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_GET_FILTERED_DATA_PATH}`
// urls to retrieve result analysis results
export const resultAnalysisTopFlowUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_ANALYSIS_TOP_FLOP_PATH}`
export const resultAnalysisVarianceUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_ANALYSIS_VARIANCE_PATH}`

// url to request available template types
export const templateTypeUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_TYPES_PATH}`
// url to retrieve the templates overview from. This endpoint provides available templates that can be retrieved
// then via the templateContentUrl
export const templateOverviewForTypeUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_OVERVIEW_FOR_TYPE_PATH}`
// url to retrieve template content from
export const templateContentUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_CONTENT_PATH}`
// url to send template for storage
export const templateSaveUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_SAVE_PATH}`
// url to post the job definitions against for execution. needs a type-parameter to validate the type of passed json
export const templateExecuteUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_EXECUTE_PATH}`


export const kolibriStateRefreshInterval = parseInt(import.meta.env.VITE_KOLIBRI_STATE_RETRIEVAL_REFRESH_INTERVAL_IN_MS)

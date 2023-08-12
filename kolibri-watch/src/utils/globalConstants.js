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
export const jobDeleteUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_DELETE_JOB_PATH}`
export const startJobUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_START_JOB_PATH}`

// url to retrieve available data files by type
export const dataFileInfoAllUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_DATA_INFO_ALL_PATH}`

export const parameterValuesSampleRequestUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_DATA_REQUEST_SAMPLE_PATH}`

//retrieve all available information retrieval metrics together with type hints
export const irMetricsAllUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_IR_METRICS_ALL_PATH}`
// transform the format given via irMetricsAllUrl to the actual jsons needed within job definition
export const irMetricsReducedToFullJsonListUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_IR_METRICS_REDUCED_TO_FULL_JSON}`

// urls to retrieve some result information
export const resultDateIdsUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_DATE_IDS_PATH}`

export const resultDateIdAndJobIdToResultFiledUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_DATEID_AND_JOBID_TO_RESULTFILE_PATH}`

export const resultContentRetrievalUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_CONTENT_RETRIEVAL_URL}`

export const resultExecutionIdsOverviewUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_OVERVIEW_PATH}`
export const resultExecutionIdsSingleResultsOverviewUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_SINGLE_RESULT_OVERVIEW_PATH}`
export const resultExecutionIdGetDataByIdUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_IDS_GET_DATA_PATH}`
// urls to retrieve result analysis results
export const resultAnalysisTopFlowUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_ANALYSIS_TOP_FLOP_PATH}`
export const resultAnalysisVarianceUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_RESULT_ANALYSIS_VARIANCE_PATH}`

// url to retrieve mapping of type to template info
export const templateAllTypeToInfoMapUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_TEMPLATE_TYPE_TO_INFO_MAP_PATH}`
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


// retrieve structure of search evaluation job definition together with endpoint
// where final definition can be thrown against to run the job
export const kolibriSearchEvalJobDefinitionUrl = `${kolibriBaseUrl}/${import.meta.env.VITE_KOLIBRI_JOBDEF_SEARCHEVAL_PATH}`

export const kolibriStateRefreshInterval = parseInt(import.meta.env.VITE_KOLIBRI_STATE_RETRIEVAL_REFRESH_INTERVAL_IN_MS)

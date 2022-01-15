import { createWebHistory, createRouter } from "vue-router";
import JobCreationView from "../views/JobCreationView.vue";
import HistoryView from "../views/HistoryView.vue";
import StatusView from "../views/StatusView.vue";
import AnalyzeView from "../views/AnalyzeView.vue";
import ResultView from "../views/ResultView.vue";
import NotFoundView from "../views/NotFoundView.vue";
import DataComposeView from "../views/DataComposeView.vue";

const routes = [
    {
        path: "/jobCreation",
        name: "JobCreation",
        component: JobCreationView
    },
    {
        path: "/dataCompose",
        name: "DataCompose",
        component: DataComposeView
    },
    {
        path: "/history",
        name: "History",
        component: HistoryView
    },
    {
        path: "/",
        name: "Status",
        component: StatusView
    },
    {
        path: "/analyze",
        name: "Analyze",
        component: AnalyzeView
    },
    {
        path: "/result",
        name: "Result",
        component: ResultView
    },
    {
        path: '/:catchAll(.*)',
        component: NotFoundView,
        name: 'NotFound'
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

export default router;
{{/*
Expand the name of the chart.
*/}}
{{- define "kolibri-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "kolibri-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}
/*
Define fullname for the compute pods
*/
{{- define "kolibri-service.fullnameCompute" -}}
{{- printf "kolibri-service-compute" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "kolibri-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "kolibri-service.labels" -}}
helm.sh/chart: {{ include "kolibri-service.chart" . }}
{{ include "kolibri-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
the app-selector label is needed for cluster forming
*/}}
{{- define "kolibri-service.selectorLabels" -}}
app: {{ .Chart.Name  }}
app.kubernetes.io/name: {{ include "kolibri-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
{{- define "kolibri-service.selectorLabelsHttp" -}}
type: httpserver
{{- end }}
{{- define "kolibri-service.selectorLabelsCompute" -}}
type: compute
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "kolibri-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "kolibri-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

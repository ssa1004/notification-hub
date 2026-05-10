{{/*
notification-hub helper templates.
*/}}

{{/* Chart name override */}}
{{- define "notification-hub.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Full name — release name + chart name (또는 fullnameOverride) */}}
{{- define "notification-hub.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Chart version label "name-version" */}}
{{- define "notification-hub.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* 표준 라벨 — 모든 리소스 metadata.labels */}}
{{- define "notification-hub.labels" -}}
helm.sh/chart: {{ include "notification-hub.chart" . }}
{{ include "notification-hub.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/* Selector 라벨 — Service / Deployment selector 와 Pod label 에서 동시 사용. 변경 금지 */}}
{{- define "notification-hub.selectorLabels" -}}
app.kubernetes.io/name: {{ include "notification-hub.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/* ServiceAccount 이름 결정 */}}
{{- define "notification-hub.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "notification-hub.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/* 사용할 Secret 이름 — mode=external 이면 existingSecretName, 아니면 fullname */}}
{{- define "notification-hub.secretName" -}}
{{- if and (eq .Values.secrets.mode "external") .Values.secrets.existingSecretName -}}
{{- .Values.secrets.existingSecretName -}}
{{- else -}}
{{- include "notification-hub.fullname" . -}}
{{- end -}}
{{- end -}}

{{/* image tag — 비어있으면 .Chart.AppVersion 사용 */}}
{{- define "notification-hub.imageTag" -}}
{{- default .Chart.AppVersion .Values.image.tag -}}
{{- end -}}

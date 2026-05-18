{{/*
=================================================================
Macros (templates nomeados) reutilizados em outros templates.
=================================================================
*/}}

{{/*
Nome completo do release: "<release>-<chart>", limitado a 63 chars (limite do K8s)
Exemplo: helm install api-dev ./productivity-api → "api-dev-productivity-api"
*/}}
{{- define "productivity-api.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Labels padrão recomendados pelo Kubernetes.
Aplicados em TODOS os recursos (Deployment, Service, etc).
*/}}
{{- define "productivity-api.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels — subset menor das labels acima.
Usado em Deployment.spec.selector e Service.spec.selector (precisa ser estável).
*/}}
{{- define "productivity-api.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

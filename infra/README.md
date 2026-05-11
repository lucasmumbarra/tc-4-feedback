# Infra (Bicep)

Este diretório contém a infraestrutura como código em **Bicep**, pensada para subir e destruir rapidamente para testes ou demonstração do Tech Challenge.

O parâmetro **`location`** é passado pelo workflow **Infra — deploy** (input *location*, típico **`eastus`** ou **`westus2`** se houver problemas de capacidade).

## Recursos provisionados (`main.bicep`)

- **Storage Account** (dados da app): Table Storage (tabela de feedbacks), **fila** `critical-feedback`, **container Blob** `relatorios` para relatórios semanais.
- **Storage Account** (runtime do Azure Functions): `AzureWebJobsStorage` isolado da conta de dados.
- **Application Insights** ligado à Function App (`APPLICATIONINSIGHTS_CONNECTION_STRING`).
- **Function App** (plano Consumption Linux, Java 21) com identidade gerida (system-assigned) e **HTTPS only**.

## Notificações por e-mail

O processamento crítico usa **SendGrid** (HTTP) quando configurado na Function App:

- `SENDGRID_API_KEY`
- `NOTIFY_FROM_EMAIL` (remetente verificado no SendGrid)
- `ADMIN_NOTIFY_EMAIL` (destino)

Se alguma destas variáveis faltar, o envio é **simulado em log** (útil para demo sem custo de e-mail).

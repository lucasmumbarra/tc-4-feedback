# Infra (Bicep)

Este diretório contém a infraestrutura como código em **Bicep**, pensada para subir e destruir rapidamente para testes ou demonstração do Tech Challenge.

O parâmetro **`location`** é passado pelo workflow **Infra — deploy** (input *location*, típico **`eastus`** ou **`westus2`** se houver problemas de capacidade).

## Recursos provisionados (`main.bicep`)

- **Storage Account** (dados da app): Table Storage (tabelas `feedbacks` e `emaillogs`), **container Blob** `relatorios` para PDFs dos relatórios semanais.
- **Storage Account** (runtime do Azure Functions): `AzureWebJobsStorage` isolado da conta de dados.
- **Application Insights** ligado à Function App (`APPLICATIONINSIGHTS_CONNECTION_STRING`).
- **Function App** (plano Consumption Linux, Java 21) com identidade gerida (system-assigned) e **HTTPS only**.

## Notificações por e-mail (SendGrid)

O envio é **síncrono** no `POST /api/avaliacao` quando a nota classifica como `CRITICA` (0–3), via **SMTP** (`smtp.sendgrid.net:587`). O resultado de cada tentativa é gravado na tabela **`emaillogs`**.

Configure na **Function App** (Application settings ou `local.settings.json`):

- `SENDGRID_API_KEY`: API key (senha SMTP; username = `apikey`).
- `NOTIFY_FROM_EMAIL`: remetente verificado no SendGrid.
- `ADMIN_NOTIFY_EMAIL`: destinatário do alerta.
- `EMAIL_LOG_TABLE_NAME`: nome da tabela de logs (default `emaillogs`, criada pelo Bicep).

Documentação: [SendGrid — SMTP](https://docs.sendgrid.com/for-developers/sending-email/getting-started-smtp).

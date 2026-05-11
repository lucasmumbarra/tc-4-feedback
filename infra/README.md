# Infra (Bicep)

Este diretório contém a infraestrutura como código em **Bicep**, pensada para subir e destruir rapidamente para testes ou demonstração do Tech Challenge.

O parâmetro **`location`** é passado pelo workflow **Infra — deploy** (input *location*, típico **`eastus`** ou **`westus2`** se houver problemas de capacidade).

## Recursos provisionados (`main.bicep`)

- **Storage Account** (dados da app): Table Storage (tabela de feedbacks), **fila** `critical-feedback`, **container Blob** `relatorios` para relatórios semanais.
- **Storage Account** (runtime do Azure Functions): `AzureWebJobsStorage` isolado da conta de dados.
- **Application Insights** ligado à Function App (`APPLICATIONINSIGHTS_CONNECTION_STRING`).
- **Function App** (plano Consumption Linux, Java 21) com identidade gerida (system-assigned) e **HTTPS only**.

## Notificações por e-mail (Azure Communication Services)

O envio usa o SDK **Azure Communication Services — Email**. O recurso **Communication Services** e o domínio de e-mail (Azure gerido ou personalizado) são normalmente criados à parte no portal ou por Bicep dedicado, porque envolve verificação de domínio e remetente.

Na **Function App**, configure como **Application settings** (ou em `local.settings.json`):

- `ACS_EMAIL_CONNECTION_STRING` (ou `AZURE_COMMUNICATION_CONNECTION_STRING`): connection string do recurso ACS.
- `NOTIFY_FROM_EMAIL`: endereço **Mail From** aprovado no ACS (ex.: `DoNotReply@<nome>.azurecomm.net`).
- `ADMIN_NOTIFY_EMAIL`: destinatário do alerta.

Documentação: [Email do Azure Communication Services](https://learn.microsoft.com/azure/communication-services/concepts/email/email-overview).

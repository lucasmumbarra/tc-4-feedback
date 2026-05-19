# Infra (Bicep)

Este diretório contém a infraestrutura como código em **Bicep**, pensada para subir e destruir rapidamente para testes ou demonstração do Tech Challenge.

O parâmetro **`location`** é passado pelo workflow **Infra — deploy** (input *location*, típico **`eastus`** ou **`westus2`** se houver problemas de capacidade).

## Recursos provisionados (`main.bicep`)

- **Storage Account** (dados da app): Table Storage (tabela de feedbacks), **fila** `critical-feedback`, **container Blob** `relatorios` para relatórios semanais.
- **Storage Account** (runtime do Azure Functions): `AzureWebJobsStorage` isolado da conta de dados.
- **Application Insights** ligado à Function App (`APPLICATIONINSIGHTS_CONNECTION_STRING`).
- **Function App** (plano Consumption Linux, Java 21) com identidade gerida (system-assigned) e **HTTPS only**.
- **`AzureWebJobsDataStorage`**: mesma connection string da conta de dados, para o runtime resolver o **QueueTrigger** (`connection="DataStorage"` no código).
- O pacote da Function App inclui **`host.json` com `extensionBundle`** (v4.x); sem o bundle o host **não** carrega a extensão de **Storage Queues** e a fila não é consumida (dequeue 0).
- **`extensions.queues.messageEncoding` = `none`**: alinha o trigger com mensagens JSON enviadas pelo SDK Java (o runtime v4 assume Base64 por defeito; sem isto aparece `Message decoding has failed` e mensagens vão para **`critical-feedback-poison`**).

## Notificações por e-mail (SendGrid)

O envio usa o SDK **SendGrid** (`sendgrid-java`). Crie uma conta em [sendgrid.com](https://sendgrid.com), gere uma API key e verifique o remetente (Single Sender ou autenticação de domínio).

Na **Function App**, configure como **Application settings** (ou em `local.settings.json`):

- `SENDGRID_API_KEY`: API key com permissão **Mail Send**.
- `NOTIFY_FROM_EMAIL`: endereço remetente verificado no SendGrid.
- `ADMIN_NOTIFY_EMAIL`: destinatário do alerta.

Documentação: [SendGrid API — Mail Send](https://docs.sendgrid.com/api-reference/mail-send/mail-send).

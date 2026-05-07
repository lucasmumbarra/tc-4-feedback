# Infra (Bicep)

Este diretório contém a infraestrutura como código em **Bicep**, pensada para subir e destruir rapidamente para testes/demonstração.

O parâmetro **`location`** é passado pelo workflow **Infra — deploy** (input *location*, típico **`eastus`** ou **`westus2`** se houver problemas de capacidade).

Recursos provisionados:
- Azure Storage Account (Queue) + fila `critical-feedback`
- Azure Cosmos DB (serverless) + database/container
- Application Insights
- Function App (Consumption) com Application Settings para o projeto

> Observação: o recurso do **Azure Communication Services (Email)** pode exigir configurações adicionais de domínio/remetente no portal. O Bicep injeta a `ACS_EMAIL_CONNECTION_STRING` e o `EMAIL_FROM` na Function App, mas o remetente precisa estar habilitado no ACS.


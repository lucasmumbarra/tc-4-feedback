# Plataforma de Feedback (Tech Challenge – Fase 4)

API serverless no Azure: **Quarkus** em **Azure Functions** (HTTP, fila e timer), **Cosmos DB**, **Storage Queue** e e-mail com **Azure Communication Services**. A infraestrutura está em **Bicep** (`infra/main.bicep`) e sobe com **GitHub Actions** (disparo manual).

## Fluxo da aplicação

1. `POST /api/avaliacao` — valida e grava no Cosmos  
2. Nota **CRÍTICA** (0–3) — envia mensagem para a fila  
3. Queue trigger — envia e-mail de urgência (ACS)  
4. Timer trigger — relatório semanal (Cosmos + ACS)

### Corpo do `POST /api/avaliacao`

```json
{ "descricao": "string", "nota": 0 }
```

**nota** (0–10): 0–3 → `CRITICA`; 4–6 → `ATENCAO`; 7–10 → `OK`.

## Stack

| Área | Tecnologia |
|------|------------|
| Runtime | Java 21, Quarkus |
| HTTP / Functions | `quarkus-azure-functions-http`, Azure Functions Java |
| Dados | Azure Cosmos DB (serverless) |
| Fila | Azure Storage Queue |
| E-mail | Azure Communication Services (Email) |
| IaC | Bicep |
| CI/CD infra | GitHub Actions (`azure/login` OIDC + `azure/arm-deploy`) |

## Funções

| Trigger | Nome no Azure | Classe |
|---------|---------------|--------|
| HTTP | — | `SubmitFeedbackFunction` → `POST /api/avaliacao` |
| Queue | `processCriticalFeedback` | `ProcessCriticalFeedbackFunction` |
| Timer | `relatorioSemanal` (segundas 09:00 UTC) | `GenerateWeeklyReportFunction` |

## Variáveis de ambiente

No Azure, o Bicep preenche a maior parte na Function App. Para desenvolvimento local, usa `local.settings.json` (não commits com segredos reais):

- `AZURE_STORAGE_CONNECTION_STRING`, `CRITICAL_FEEDBACK_QUEUE_NAME` (default `critical-feedback`)
- `COSMOS_ENDPOINT`, `COSMOS_KEY`, `COSMOS_DATABASE` (`feedbackdb`), `COSMOS_CONTAINER` (`feedbacks`)
- `ACS_EMAIL_CONNECTION_STRING`, `EMAIL_FROM`, `ADMIN_EMAIL_TO`
- Opcionais: `APPLICATIONINSIGHTS_CONNECTION_STRING`, `AzureWebJobsStorage`

## Desenvolvimento local

Requisito: **Java 21**.

```bash
./gradlew test
./gradlew quarkusDev
```

A raiz HTTP da API está em `/api` (`quarkus.http.root-path`).

## Infraestrutura (GitHub Actions)

Os workflows **Infra — deploy** e **Infra — destroy** só correm quando inicias manualmente: **Actions** → escolhe o workflow → **Run workflow**.

- **RG do ACS/Email (manual)**: mantém o Resource Group do Azure Communication Services fora do ciclo de deploy/destroy da infra.
- **RG da infra (automatizado)**: o workflow cria/usa `rg-{prefix}-{environment}-infra` e o destroy apaga esse RG.

Detalhes dos recursos Bicep: [`infra/README.md`](infra/README.md).

### Pré-requisitos no Azure

- **Azure Communication Services** com e-mail configurado (domínio/remetente no Portal). O Bicep precisa da connection string; não cria o recurso ACS.
- Identidade para o GitHub: **Microsoft Entra app** (ou managed identity) com **federated credential** (OIDC) a confiar no repositório, e **permissão** na subscription ou no resource group (por exemplo *Contributor*).

### Secrets do repositório

Em **Settings → Secrets and variables → Actions**:

| Secret | Descrição |
|--------|-----------|
| `AZURE_CLIENT_ID` | Application (client) ID |
| `AZURE_TENANT_ID` | Directory (tenant) ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID |
| `ACS_EMAIL_CONNECTION_STRING` | Connection string do ACS (Email) |

Guia Microsoft: [Deploy Bicep com GitHub Actions](https://learn.microsoft.com/azure/azure-resource-manager/bicep/deploy-github-actions). Action: [azure/arm-deploy](https://github.com/marketplace/actions/deploy-azure-resource-manager-arm-template).

### Destroy

Workflow **Infra — destroy**: indica o nome do Resource Group, escreve **DESTROY** no campo de confirmação e executa. Opcionalmente usa delete em segundo plano (*no wait*).

Se o **Cosmos** falhar por capacidade na região, volta a correr o deploy com outra **location** (por exemplo `westus2`).

## Monitoramento

Application Insights está ligado à Function App no Bicep. No Portal: **Monitor** / **Log stream**; em Application Insights, **Logs**.

## Referências

- [Quarkus](https://quarkus.io/)
- [Azure Functions HTTP (Quarkus)](https://quarkus.io/guides/azure-functions-http)

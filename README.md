# Plataforma de Feedback (Tech Challenge – Fase 4)

Aplicação **serverless em Java 21 (Quarkus)** no **Azure Functions**, alinhada ao enunciado: receber avaliações, notificar administradores em casos críticos e gerar **relatório semanal** com agregações. A persistência é **Azure Table Storage** (não se usa Cosmos DB).

## Arquitetura (resumo)

| Função Azure | Gatilho | Responsabilidade |
|--------------|---------|-------------------|
| `submitFeedback` | HTTP `POST /api/avaliacao` | Valida payload, classifica urgência, grava na tabela e, se `CRITICA` (nota 0–3), **envia e-mail** via SendGrid e regista log em Table Storage. |
| `generateWeeklyReport` | **Timer** (segundas, 09:00 UTC) | Lê feedbacks dos últimos 7 dias (UTC), calcula **média**, contagens por dia e por urgência, grava ficheiro no **Blob** `relatorios/`. |
| `QuarkusHttp` | HTTP (catch-all) | Runtime REST Quarkus (não expõe regra de negócio dedicada). |

Observabilidade: **Application Insights** (connection string nas app settings da Function App). Segurança: **HTTPS**, storage sem acesso público anónimo, **Managed Identity** na app (evolução natural para RBAC em secrets/Key Vault fora do escopo mínimo).

## Endpoint

`POST /api/avaliacao`

```json
{ "descricao": "string", "nota": 0 }
```

Regras:

- **descricao** obrigatória (não vazia).
- **nota** entre 0 e 10.
- Urgência: 0–3 → `CRITICA`; 4–6 → `ATENCAO`; 7–10 → `OK`.
- Se `CRITICA`: após gravar o feedback, o sistema chama `SendCriticalEmailFunction` (SendGrid + log na tabela `emaillogs`).

## Variáveis de ambiente

Obrigatórias (local `local.settings.json` e Azure **Configuration**):

| Variável | Descrição |
|----------|-----------|
| `AZURE_STORAGE_CONNECTION_STRING` | Connection string da conta com Table + Blob. |
| `FEEDBACK_TABLE_NAME` | Nome da tabela de feedbacks (default `feedbacks`). |
| `EMAIL_LOG_TABLE_NAME` | Nome da tabela de logs de e-mail (default `emaillogs`). |
| `WEEKLY_REPORT_CONTAINER` | Container Blob dos relatórios (default `relatorios`). |

Opcionais:

| Variável | Descrição |
|----------|-----------|
| `AZURE_HTTP_TIMEOUT_SECONDS` | Timeout SDK (default `10`). |
| `SENDGRID_API_KEY` | API key do **SendGrid** (Settings → API Keys). |
| `NOTIFY_FROM_EMAIL` | Remetente verificado no SendGrid (Single Sender ou domínio autenticado). |
| `ADMIN_NOTIFY_EMAIL` | Destino dos alertas críticos. |

Sem API key SendGrid ou sem remetente/destino, o alerta crítico é registado com `mode=SIMULATED` na tabela `emaillogs` e nos logs da função.

### Por que o e-mail pode “não chegar”

1. **App settings na Function App**: `SENDGRID_API_KEY`, `NOTIFY_FROM_EMAIL`, `ADMIN_NOTIFY_EMAIL` têm de estar definidas. Se faltar alguma, o código regista `notifyCritical.mode=SIMULATED` e grava o log com o mesmo modo.
2. **SendGrid**: remetente tem de estar verificado; API key com permissão **Mail Send**.
3. **Tabela de logs**: consulte `emaillogs` no Storage Explorer — cada tentativa de envio crítico gera uma linha com `mode` (`SENT`, `SIMULATED`, `SENDGRID_FAILED`, `EXCEPTION`).

## Tabelas (Table Storage)

| Tabela | Conteúdo |
|--------|----------|
| `feedbacks` | Avaliações (partitionKey = dia UTC, rowKey = id). |
| `emaillogs` | Logs de envio de e-mail crítico (`feedbackId`, `mode`, `statusCode`, `fromEmail`, `toEmail`, etc.). |

## Build e pacote Azure Functions

Requisito: **Java 21**.

```bash
mvn -B package
```

Gera `target/azure-functions/<appName>/` com `function.json` por função. O nome da app vem de `-Dquarkus.azure-functions.app-name=...` ou da propriedade Maven `function.appName` no `pom.xml`.

## Infra e CI/CD

- **Bicep**: `infra/main.bicep` — storage de dados (tabelas + container blob), storage do runtime, App Insights, Function App.
- **GitHub Actions**: `/.github/workflows` — deploy da infra (manual) e deploy do pacote Java (OIDC + RBAC).

## Desenvolvimento local

Pode usar **Azurite** (`docker-compose.yml`) para Blob e Table; a connection string segue o formato documentado para Azurite.

## Documentação das funções (enunciado)

- **E-mail de urgência**: descrição, urgência, data de envio (UTC, ISO-8601).
- **Relatório semanal**: para cada avaliação no período — descrição, urgência, data de envio; mais quantidade por dia, quantidade por urgência e **média das notas**.

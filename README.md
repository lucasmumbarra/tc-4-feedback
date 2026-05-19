# Plataforma de Feedback (Tech Challenge – Fase 4)

Aplicação **serverless em Java 21 (Quarkus)** no **Azure Functions**, alinhada ao enunciado: receber avaliações, notificar administradores em casos críticos e gerar **relatório semanal** com agregações. A persistência é **Azure Table Storage** (não se usa Cosmos DB).

## Arquitetura (resumo)

| Função Azure | Gatilho | Responsabilidade |
|--------------|---------|-------------------|
| `submitFeedback` | HTTP `POST /api/avaliacao` (via `QuarkusHttp`) | Valida, grava feedback; se `CRITICA`, chama `SendCriticalEmailFunction.process()` (CDI). |
| `sendCriticalEmail` | HTTP `POST /api/send-critical-email` | Azure Function + endpoint Quarkus; envia e-mail e grava `emaillogs`. Testável no portal (Run) ou via POST. |
| `generateWeeklyReport` | **Timer** (segundas, 09:00 UTC) | Relatório semanal no Blob `relatorios/`. |
| `QuarkusHttp` | HTTP (catch-all) | Runtime REST Quarkus (`/api/avaliacao`). |

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
- Se `CRITICA`: após gravar, executa a lógica de `sendCriticalEmail` no mesmo worker (não via HTTP interno — `QuarkusHttp` captura `/api/*` e causava 404). Erros ficam em `emaillogs`.

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
| `SENDGRID_API_KEY` | API key do SendGrid — usada como **senha SMTP** (Settings → API Keys). |
| `NOTIFY_FROM_EMAIL` | Remetente verificado no SendGrid (Single Sender ou domínio autenticado). |
| `ADMIN_NOTIFY_EMAIL` | Destino dos alertas críticos. |
| `SMTP_HOST` | Opcional (default `smtp.sendgrid.net`). |
| `SMTP_PORT` | Opcional (default `587`). |
| `SMTP_USERNAME` | Opcional (default `apikey`). |

Sem as três variáveis obrigatórias, o alerta crítico é registado com `mode=SIMULATED` na tabela `emaillogs` (como no seu teste: `errorDetail` lista o que falta).

### Por que o e-mail pode “não chegar”

1. **Variáveis na Function App em runtime** (não basta só no GitHub): o código lê `System.getenv()` na Azure. Pode configurar no **Portal** (Configuration) ou deixar o workflow **Deploy Java project** gravar os secrets do environment **Actions** (`SENDGRID_API_KEY`, `NOTIFY_FROM_EMAIL`, `ADMIN_NOTIFY_EMAIL`) via `az functionapp config appsettings set` após cada deploy.
2. **SendGrid**: remetente verificado; API key válida (username SMTP = `apikey`, password = a API key).
3. **`sendCriticalEmail` no portal**: aparece como function HTTP; teste com **Run** no portal ou `POST /api/send-critical-email`. O submit usa a mesma lógica via CDI (logs `sendCriticalEmail.process.*`).
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

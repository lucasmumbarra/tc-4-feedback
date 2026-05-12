# Plataforma de Feedback (Tech Challenge – Fase 4)

Aplicação **serverless em Java 21 (Quarkus)** no **Azure Functions**, alinhada ao enunciado: receber avaliações, notificar administradores em casos críticos e gerar **relatório semanal** com agregações. A persistência é **Azure Table Storage** (não se usa Cosmos DB).

## Arquitetura (resumo)

| Função Azure | Gatilho | Responsabilidade |
|--------------|---------|-------------------|
| `submitFeedback` | HTTP `POST /api/avaliacao` | Valida payload, classifica urgência, grava na tabela e **enfileira** mensagens só para `CRITICA`. |
| `processCriticalFeedback` | **Azure Queue** (`critical-feedback`) | Lê a mensagem e dispara **notificação** ao administrador (**Azure Communication Services — Email** ou log simulado). |
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

## Variáveis de ambiente

Obrigatórias (local `local.settings.json` e Azure **Configuration**):

| Variável | Descrição |
|----------|-----------|
| `AZURE_STORAGE_CONNECTION_STRING` | Connection string da conta com Table + Queue + Blob. |
| `FEEDBACK_TABLE_NAME` | Nome da tabela (default `feedbacks`). |
| `CRITICAL_FEEDBACK_QUEUE_NAME` | Opcional: altera só para onde o **HTTP** envia mensagens (default `critical-feedback`). O **trigger** da função usa o mesmo nome em código (`FeedbackQueueNames`); se mudares um, alinha o outro e o Bicep. |
| `WEEKLY_REPORT_CONTAINER` | Container Blob dos relatórios (default `relatorios`). |

Opcionais:

| Variável | Descrição |
|----------|-----------|
| `AZURE_HTTP_TIMEOUT_SECONDS` | Timeout SDK (default `10`). |
| `ACS_EMAIL_CONNECTION_STRING` | Connection string do **Azure Communication Services** (recurso com Email ativo). Alternativa: `AZURE_COMMUNICATION_CONNECTION_STRING`. |
| `NOTIFY_FROM_EMAIL` | Remetente no formato exigido pelo ACS (ex.: endereço `DoNotReply` no subdomínio `*.azurecomm.net` do recurso, ou domínio personalizado verificado no portal). |
| `ADMIN_NOTIFY_EMAIL` | Destino dos alertas críticos. |

Sem connection string ACS ou sem remetente/destino, o alerta crítico aparece nos **logs** da função com a linha `notifyCritical.mode=SIMULATED` e indica **quais** variáveis faltam.

### Por que o e-mail pode “não chegar” mesmo com mensagem na fila

1. **Teste “Run” no portal** em funções **Queue** em Linux Consumption costuma falhar com `Failed to fetch` (CORS/rede do browser). Isso **não** prova que a função não corre; use **Log stream**, **Application Insights** ou deixe a mensagem na fila e espere o host consumir.
2. **App settings na Function App**: `ACS_EMAIL_CONNECTION_STRING` (ou `AZURE_COMMUNICATION_CONNECTION_STRING`), `NOTIFY_FROM_EMAIL`, `ADMIN_NOTIFY_EMAIL` têm de estar definidas no **mesmo** Function App que processa a fila. Se faltar alguma, o código só regista `notifyCritical.mode=SIMULATED`.
3. **Fila e connection**: o trigger usa `AZURE_STORAGE_CONNECTION_STRING` e o nome fixo da fila **`critical-feedback`** (igual ao Bicep e ao `submitFeedback`). A mensagem tem de estar nessa fila na **mesma** conta de storage que a app usa. A variável `CRITICAL_FEEDBACK_QUEUE_NAME` só altera o **produtor** se quiseres outro nome (mantém o trigger e o Bicep alinhados ao mudar).
4. **ACS Email**: no portal, Communication Services → Email → domínio e **Mail From** aprovados; `NOTIFY_FROM_EMAIL` tem de coincidir **exatamente** com o endereço aprovado. Erros da API aparecem como `notifyCritical.mode=ACS_FAILED` ou `acs_email_exception` nos logs.


## Build e pacote Azure Functions

Requisito: **Java 21**.

```bash
mvn -B package
```

Gera `target/azure-functions/<appName>/` com `function.json` por função. O nome da app vem de `-Dquarkus.azure-functions.app-name=...` ou da propriedade Maven `function.appName` no `pom.xml`.

## Infra e CI/CD

- **Bicep**: `infra/main.bicep` — storage de dados (tabela + fila + container), storage do runtime, App Insights, Function App.
- **GitHub Actions**: `/.github/workflows` — deploy da infra (manual) e deploy do pacote Java (OIDC + RBAC).

## Desenvolvimento local

Pode usar **Azurite** (`docker-compose.yml`) para Blob, Queue e Table; a connection string segue o formato documentado para Azurite.

## Documentação das funções (enunciado)

- **E-mail de urgência**: descrição, urgência, data de envio (UTC, ISO-8601 na mensagem).
- **Relatório semanal**: para cada avaliação no período — descrição, urgência, data de envio; mais quantidade por dia, quantidade por urgência e **média das notas**.

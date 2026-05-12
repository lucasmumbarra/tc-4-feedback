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
| `AzureWebJobsDataStorage` | **Obrigatório para o QueueTrigger**: mesma connection string que `AZURE_STORAGE_CONNECTION_STRING` (conta de dados). O runtime mapeia `connection="DataStorage"` → `AzureWebJobsDataStorage`. |
| `WEEKLY_REPORT_CONTAINER` | Container Blob dos relatórios (default `relatorios`). |

Opcionais:

| Variável | Descrição |
|----------|-----------|
| `AZURE_HTTP_TIMEOUT_SECONDS` | Timeout SDK (default `10`). |
| `CRITICAL_FEEDBACK_QUEUE_NAME` | Opcional: nome da fila no **produtor** (default `critical-feedback`). O trigger usa nome fixo em código. |
| `ACS_EMAIL_CONNECTION_STRING` | Connection string do **Azure Communication Services** (recurso com Email ativo). Alternativa: `AZURE_COMMUNICATION_CONNECTION_STRING`. |
| `NOTIFY_FROM_EMAIL` | Remetente no formato exigido pelo ACS (ex.: endereço `DoNotReply` no subdomínio `*.azurecomm.net` do recurso, ou domínio personalizado verificado no portal). |
| `ADMIN_NOTIFY_EMAIL` | Destino dos alertas críticos. |

Sem connection string ACS ou sem remetente/destino, o alerta crítico aparece nos **logs** da função com a linha `notifyCritical.mode=SIMULATED` e indica **quais** variáveis faltam.

### Por que o e-mail pode “não chegar” mesmo com mensagem na fila

1. **Teste “Run” no portal** em funções **Queue** em Linux Consumption costuma falhar com `Failed to fetch` (HTTP 0): o browser do portal não consegue invocar o endpoint de teste (CORS/rede). **Não uses** “Run” em `processCriticalFeedback` como prova de funcionamento. O Bicep inclui CORS para `https://portal.azure.com`, o que ajuda em alguns casos, mas o método fiável é: **Log stream** / **Application Insights** enquanto envias um `POST /api/avaliacao` com nota 0–3 (ou uma mensagem na fila).
2. **App settings na Function App**: `ACS_EMAIL_CONNECTION_STRING` (ou `AZURE_COMMUNICATION_CONNECTION_STRING`), `NOTIFY_FROM_EMAIL`, `ADMIN_NOTIFY_EMAIL` têm de estar definidas no **mesmo** Function App que processa a fila. Se faltar alguma, o código só regista `notifyCritical.mode=SIMULATED`.
3. **Fila**: nome fixo **`critical-feedback`** (trigger e produtor alinhados ao Bicep). A conta de storage tem de ser a mesma que em `AZURE_STORAGE_CONNECTION_STRING` (SDK) e em **`AzureWebJobsDataStorage`** (host do Functions para o QueueTrigger). A variável `CRITICAL_FEEDBACK_QUEUE_NAME` só altera o **produtor** se mudares o nome da fila no código/Bicep em conjunto.
4. **Queue trigger sem consumo (Dequeue count = 0)**:
   - **`host.json` tem de incluir `extensionBundle`** (Storage Queues). Sem isto o host **não** regista o listener da fila — sintoma típico: mensagens na fila e dequeue sempre 0. O repositório já inclui o bundle `[4.0.0, 5.0.0)`; volta a fazer **deploy do pacote** da Function App.
   - **`extensions.queues.messageEncoding` = `none`**: o `QueueTrigger` do Functions v4 assume **Base64** por defeito; o `QueueClient` Java envia **JSON em texto**. Sem `none`, o host regista `Message decoding has failed`, incrementa dequeue até 5 e move para **`critical-feedback-poison`** — a função **nunca** corre e **não há e-mail**.
   - **`AzureWebJobsDataStorage`**: mesma connection string da conta onde está `critical-feedback` (definido no Bicep). O binding usa `connection="DataStorage"` → `AzureWebJobsDataStorage`.

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

# Plataforma de Feedback (Tech Challenge – Fase 4)

Projeto serverless para recebimento de feedbacks, notificação de itens críticos e geração de relatório semanal. A solução foi montada para ser simples de subir/destruir e ficar **100% Azure**.

## Visão geral

O sistema recebe avaliações via HTTP, persiste os dados, dispara notificação quando a avaliação é crítica e gera um relatório semanal com agregações.

- **Entrada**: HTTP (Azure Functions via Quarkus)
- **Persistência**: Azure Cosmos DB (NoSQL)
- **Mensageria**: Azure Storage Queue
- **E-mail**: Azure Communication Services (Email)

## Arquitetura (alto nível)

1. Cliente envia `POST /api/avaliacao`
2. A Function HTTP valida e salva no Cosmos DB
3. Se a avaliação for **CRITICA**, publica uma mensagem na Storage Queue
4. Uma Function (Queue Trigger) consome a mensagem e envia o e-mail de urgência
5. Uma Function (Timer Trigger) roda semanalmente, consulta o Cosmos DB, agrega e envia o relatório

## Endpoints

### `POST /api/avaliacao`

Payload:

```json
{
  "descricao": "string",
  "nota": 0
}
```

Regras:
- **nota**: inteiro de 0 a 10
- **urgência** (derivada da nota):
  - 0 a 3: `CRITICA`
  - 4 a 6: `ATENCAO`
  - 7 a 10: `OK`

## Funções

### HTTP Trigger (ingestão)
- **Rota**: `POST /api/avaliacao`
- **Classe**: `br.com.fiap.tc.feedback.function.http.SubmitFeedbackFunction`
- **Responsabilidade**: validar, persistir no Cosmos DB e publicar mensagem na fila quando for crítico

### Queue Trigger (notificação de crítico)
- **FunctionName**: `processCriticalFeedback`
- **Classe**: `br.com.fiap.tc.feedback.function.event.ProcessCriticalFeedbackFunction`
- **Responsabilidade**: consumir a fila e enviar e-mail de urgência

### Timer Trigger (relatório semanal)
- **FunctionName**: `relatorioSemanal`
- **Classe**: `br.com.fiap.tc.feedback.function.event.GenerateWeeklyReportFunction`
- **Schedule**: segunda-feira às 09:00 UTC (`0 0 9 * * 1`)
- **Responsabilidade**: consultar os últimos 7 dias, calcular média e contagens e enviar o e-mail do relatório

## Configuração (local e Azure)

As variáveis abaixo existem em `local.settings.json` e devem ser configuradas também em **Application Settings** no Azure.

- `AZURE_STORAGE_CONNECTION_STRING`: Storage Account (Queue)
- `CRITICAL_FEEDBACK_QUEUE_NAME`: nome da fila (default `critical-feedback`)

- `COSMOS_ENDPOINT`: endpoint do Cosmos DB
- `COSMOS_KEY`: key do Cosmos DB
- `COSMOS_DATABASE`: default `feedbackdb`
- `COSMOS_CONTAINER`: default `feedbacks`

- `ACS_EMAIL_CONNECTION_STRING`: connection string do Azure Communication Services (Email)
- `EMAIL_FROM`: remetente configurado no ACS Email
- `ADMIN_EMAIL_TO`: destinatário (administradores)

## Rodando localmente

Pré-requisitos:
- Java 21

Comandos:

```bash
./gradlew.bat test
./gradlew.bat quarkusDev
```

Observação: o `quarkus.http.root-path=/api` está configurado em `src/main/resources/application.properties`.

## Estrutura do código

O projeto segue uma separação por responsabilidade (inspirada em Clean Architecture):

- `function/`: entradas (HTTP e triggers)
- `application/`: DTOs e contratos de aplicação
- `domain/`: regras e modelos centrais
- `infrastructure/`: integrações com serviços Azure (Cosmos, Queue, ACS Email)

## Referências

- Quarkus: `https://quarkus.io/`
- Quarkus Azure Functions HTTP: `https://quarkus.io/guides/azure-functions-http`

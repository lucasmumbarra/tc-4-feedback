# Plataforma de Feedback (Tech Challenge – Fase 4)

Este repositório foi reiniciado **em partes**. Nesta primeira etapa temos apenas o mínimo para funcionar na nuvem:

- **1 Azure Function HTTP (Java 21)**: `POST /api/avaliacao`
- Persistência em **Azure Table Storage** (uma tabela dentro de um Storage Account)
- Infra mínima em **Bicep** (`infra/main.bicep`)
- Deploy automatizado via **GitHub Actions** (mantendo os workflows em `/.github/workflows`)

## Endpoint

`POST /api/avaliacao`

```json
{ "descricao": "string", "nota": 0 }
```

Regras:
- **descricao** é obrigatória
- **nota** deve estar entre 0 e 10
- urgência: 0–3 → `CRITICA`; 4–6 → `ATENCAO`; 7–10 → `OK`

## Variáveis de ambiente

Para desenvolvimento local (`local.settings.json`) e no Azure (App Settings):

- `AZURE_STORAGE_CONNECTION_STRING` (obrigatória)
- `FEEDBACK_TABLE_NAME` (default `feedbacks`)
- `AZURE_HTTP_TIMEOUT_SECONDS` (default `10`)

## Build

Requisito: **Java 21**.

- **Local**:

```bash
mvn -B package
```

Isso gera o pacote em `target/azure-functions/<appName>`.

## Infra (GitHub Actions)

Use o workflow **Infra — deploy** para subir um Resource Group e os recursos mínimos:

- Storage Account (com Table service + tabela)
- Function App (Consumption)
- Application Insights


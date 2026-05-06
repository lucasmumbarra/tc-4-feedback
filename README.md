# feedback

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Tech Challenge (Fase 4) - Plataforma de Feedback

### Endpoints

- `POST /api/avaliacao`

Body:

```json
{
  "descricao": "string",
  "nota": 0
}
```

Regras:
- `nota`: inteiro de 0 a 10
- A urgência é derivada da nota: `0-3` = **CRITICA**, `4-6` = **ATENCAO**, `7-10` = **OK**

### Funções serverless

- **Ingestão HTTP**: via Quarkus REST (mapeado para Azure Functions HTTP pelo `quarkus-azure-functions-http`)
- **Notificação de urgência**: Queue Trigger `processCriticalFeedback` (consome Storage Queue)
- **Relatório semanal**: Timer Trigger `relatorioSemanal` (segunda-feira 09:00 UTC)

### Variáveis de ambiente (local / Azure Functions)

Configurar em `local.settings.json` (para rodar local) e em Application Settings (no Azure):

- `AZURE_STORAGE_CONNECTION_STRING`: connection string do Storage Account (Queue)
- `CRITICAL_FEEDBACK_QUEUE_NAME`: default `critical-feedback`
- `COSMOS_ENDPOINT`: endpoint do CosmosDB
- `COSMOS_KEY`: key do CosmosDB
- `COSMOS_DATABASE`: default `feedbackdb`
- `COSMOS_CONTAINER`: default `feedbacks`
- `ACS_EMAIL_CONNECTION_STRING`: connection string do Azure Communication Services (Email)
- `EMAIL_FROM`: remetente (domínio configurado no ACS Email)
- `ADMIN_EMAIL_TO`: destinatário (admin)

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/feedback-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and
  Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on
  it.
- Azure Functions HTTP ([guide](https://quarkus.io/guides/azure-functions-http)): Write Microsoft Azure functions
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus
  REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it

## Provided Code

### REST

Este projeto foi iniciado a partir de um template do Quarkus, mas os exemplos de endpoints (`/hello`) foram removidos para manter apenas os componentes do Tech Challenge.

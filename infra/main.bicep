targetScope = 'resourceGroup'

@description('Prefixo para nomear recursos (use algo curto, ex: tc4fb)')
param prefix string

@description('Ambiente para compor nomes (ex.: demo, dev, prod).')
param envName string = 'demo'

@description('Regiao dos recursos (input location do workflow Infra deploy; ex.: eastus, westus2).')
param location string = resourceGroup().location

@description('Nome da tabela no Azure Table Storage')
param feedbackTableName string = 'feedbacks'

@description('Nome da tabela de logs de envio de e-mail')
param emailLogTableName string = 'emaillogs'

@description('Timeout (segundos) para chamadas do SDK Azure no runtime')
param azureHttpTimeoutSeconds int = 10

@description('Nome base da Function App (sem sufixo). Ex.: tc4fb-func')
param functionAppBaseName string = '${prefix}-func'

@description('Adicionar sufixo único no nome da Function App (útil quando precisa de nomes globais sem conflito)')
param functionAppUseUniqueSuffix bool = false

// Nomes de Storage: max 24 caracteres (min 3).
// Importante: Storage Account name precisa ser globalmente único. Aqui fica determinístico por prefix+ambiente.
// Convenção:
// - Table Storage: {prefix}{envName}tblstg
// - Functions runtime storage: {prefix}{envName}fnstg
var tableStorageName = toLower(replace('${prefix}${envName}tblstg', '-', ''))
var funcStorageName = toLower(replace('${prefix}${envName}fnstg', '-', ''))
var appInsightsName = '${prefix}-appi'
var planName = '${prefix}-plan'
var functionAppName = functionAppUseUniqueSuffix
  ? toLower(replace('${functionAppBaseName}-${uniqueString(resourceGroup().id)}', '_', '-'))
  : toLower(replace(functionAppBaseName, '_', '-'))

resource tableStorage 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: tableStorageName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: false
  }
}

resource tableService 'Microsoft.Storage/storageAccounts/tableServices@2023-01-01' = {
  parent: tableStorage
  name: 'default'
}

resource feedbackTable 'Microsoft.Storage/storageAccounts/tableServices/tables@2023-01-01' = {
  parent: tableService
  name: feedbackTableName
}

resource emailLogTable 'Microsoft.Storage/storageAccounts/tableServices/tables@2023-01-01' = {
  parent: tableService
  name: emailLogTableName
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-01-01' = {
  parent: tableStorage
  name: 'default'
  properties: {
    deleteRetentionPolicy: {
      enabled: false
    }
  }
}

resource reportsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  parent: blobService
  name: 'relatorios'
  properties: {
    publicAccess: 'None'
  }
}

// Storage dedicado para o runtime do Azure Functions (AzureWebJobsStorage).
resource funcStorage 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: funcStorageName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: false
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
  }
}

resource plan 'Microsoft.Web/serverfarms@2022-09-01' = {
  name: planName
  location: location
  sku: {
    name: 'Y1'
    tier: 'Dynamic'
  }
  properties: {
    // Necessário para plano Linux (Consumption).
    reserved: true
  }
}

resource functionApp 'Microsoft.Web/sites@2022-09-01' = {
  name: functionAppName
  location: location
  kind: 'functionapp,linux'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      // Garante runtime Java 21 no Linux.
      linuxFxVersion: 'Java|21'
      // O painel "Code + Test" do portal faz pedidos a partir de https://portal.azure.com (CORS).
      cors: {
        allowedOrigins: [
          'https://portal.azure.com'
        ]
        supportCredentials: false
      }
      appSettings: [
        // Functions runtime
        { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
        { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
        { name: 'JAVA_VERSION', value: '21' }
        { name: 'AzureWebJobsStorage', value: 'DefaultEndpointsProtocol=https;AccountName=${funcStorage.name};AccountKey=${funcStorage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}' }
        { name: 'WEBSITE_RUN_FROM_PACKAGE', value: '1' }

        // Observability
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
        { name: 'APPINSIGHTS_INSTRUMENTATIONKEY', value: appInsights.properties.InstrumentationKey }
        { name: 'ApplicationInsightsAgent_EXTENSION_VERSION', value: '~3' }
        { name: 'XDT_MicrosoftApplicationInsights_Mode', value: 'recommended' }

        // App settings (Table Storage + blob) — código Java lê AZURE_STORAGE_CONNECTION_STRING
        { name: 'AZURE_STORAGE_CONNECTION_STRING', value: 'DefaultEndpointsProtocol=https;AccountName=${tableStorage.name};AccountKey=${tableStorage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}' }
        { name: 'FEEDBACK_TABLE_NAME', value: feedbackTableName }
        { name: 'EMAIL_LOG_TABLE_NAME', value: emailLogTableName }

        // Relatórios (mesmo storage account da tabela)
        { name: 'WEEKLY_REPORT_CONTAINER', value: reportsContainer.name }

        // App settings (SDK timeouts)
        { name: 'AZURE_HTTP_TIMEOUT_SECONDS', value: string(azureHttpTimeoutSeconds) }
      ]
    }
  }
}

output resourceGroupName string = resourceGroup().name
output locationOut string = location
output functionAppName string = functionApp.name
output feedbackTableNameOut string = feedbackTableName
output emailLogTableNameOut string = emailLogTableName
output tableStorageAccountName string = tableStorage.name
output funcStorageAccountName string = funcStorage.name


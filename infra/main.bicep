targetScope = 'resourceGroup'

@description('Prefixo para nomear recursos (use algo curto, ex: tc4fb)')
param prefix string

@description('Regiao dos recursos (input location do workflow Infra deploy; ex.: eastus, westus2).')
param location string = resourceGroup().location

@description('Nome da fila para feedback crítico')
param criticalQueueName string = 'critical-feedback'

@description('Nome da tabela no Azure Table Storage')
param feedbackTableName string = 'feedbacks'

@description('E-mail remetente (deve estar configurado no ACS Email)')
param emailFrom string = ''

@description('E-mail do(s) administrador(es) que recebem alertas/relatórios')
param adminEmailTo string = ''

@description('Connection string do Azure Communication Services (Email)')
@secure()
param acsEmailConnectionString string

// Nomes de Storage: max 24 caracteres (min 3). prefix+funcstg+uniqueString estourava; usar fstg.
var storageName = toLower(replace('${prefix}stg${uniqueString(resourceGroup().id)}', '-', ''))
var funcStorageName = toLower(replace('${prefix}fstg${uniqueString(resourceGroup().id)}', '-', ''))
var appInsightsName = '${prefix}-appi'
var planName = '${prefix}-plan'
var functionAppName = toLower(replace('${prefix}-func-${uniqueString(resourceGroup().id)}', '_', '-'))

resource storage 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageName
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

resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-01-01' = {
  parent: storage
  name: 'default'
}

resource criticalQueue 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-01-01' = {
  parent: queueService
  name: criticalQueueName
}

// Storage dedicado para a Function App (requisito do Functions runtime)
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
  properties: {}
}

resource functionApp 'Microsoft.Web/sites@2022-09-01' = {
  name: functionAppName
  location: location
  kind: 'functionapp'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      appSettings: [
        // Functions runtime
        { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
        { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
        { name: 'AzureWebJobsStorage', value: 'DefaultEndpointsProtocol=https;AccountName=${funcStorage.name};AccountKey=${funcStorage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}' }
        { name: 'WEBSITE_RUN_FROM_PACKAGE', value: '1' }

        // Observability
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
        { name: 'APPINSIGHTS_INSTRUMENTATIONKEY', value: appInsights.properties.InstrumentationKey }
        { name: 'ApplicationInsightsAgent_EXTENSION_VERSION', value: '~3' }
        { name: 'XDT_MicrosoftApplicationInsights_Mode', value: 'recommended' }

        // App settings (Queue)
        { name: 'AZURE_STORAGE_CONNECTION_STRING', value: 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}' }
        { name: 'CRITICAL_FEEDBACK_QUEUE_NAME', value: criticalQueueName }

        // App settings (Table Storage)
        { name: 'FEEDBACK_TABLE_NAME', value: feedbackTableName }

        // App settings (ACS Email)
        { name: 'ACS_EMAIL_CONNECTION_STRING', value: acsEmailConnectionString }
        { name: 'EMAIL_FROM', value: emailFrom }
        { name: 'ADMIN_EMAIL_TO', value: adminEmailTo }
      ]
    }
  }
}

output resourceGroupName string = resourceGroup().name
output locationOut string = location
output functionAppName string = functionApp.name
output feedbackTableNameOut string = feedbackTableName
output storageAccountName string = storage.name


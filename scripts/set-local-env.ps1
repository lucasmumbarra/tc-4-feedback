$ErrorActionPreference = "Stop"

# Carrega variáveis do .env (formato KEY=VALUE)
function Load-DotEnv([string]$path) {
  if (!(Test-Path $path)) {
    throw "Arquivo não encontrado: $path"
  }

  Get-Content $path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0) { return }
    if ($line.StartsWith("#")) { return }

    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }

    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1)

    # Mantém exatamente o valor (não remove ';' etc)
    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
  }
}

Load-DotEnv (Join-Path $PSScriptRoot "..\.env.example")

Write-Host "Variáveis carregadas no processo atual:"
Write-Host "  AZURE_STORAGE_CONNECTION_STRING=$($env:AZURE_STORAGE_CONNECTION_STRING.Substring(0, [Math]::Min(60, $env:AZURE_STORAGE_CONNECTION_STRING.Length)))..."
Write-Host "  FEEDBACK_TABLE_NAME=$env:FEEDBACK_TABLE_NAME"
Write-Host "  AzureWebJobsDataStorage=(igual AZURE_STORAGE para o QueueTrigger local)"
Write-Host "  AZURE_HTTP_TIMEOUT_SECONDS=$env:AZURE_HTTP_TIMEOUT_SECONDS"


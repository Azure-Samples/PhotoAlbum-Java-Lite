param(
    [Parameter(Mandatory = $true)]
    [string]$Suffix,

    [Parameter(Mandatory = $true)]
    [string]$Location = "Japan West"
)

$ErrorActionPreference = "Stop"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-WarningMessage {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-ErrorMessage {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

Write-Info "=== Azure Photo Album Resources Setup ==="
az extension add --allow-preview --upgrade --yes --name rdbms-connect

# Variables
$ResourceSuffix = if ([string]::IsNullOrWhiteSpace($Suffix)) {
    [Guid]::NewGuid().ToString("N").Substring(0, 6)
}
else {
    $Suffix.ToLowerInvariant()
}

if ($ResourceSuffix -notmatch '^[a-z0-9]{3,12}$') {
    throw "Invalid suffix '$ResourceSuffix'. Use 3-12 lowercase letters or numbers (a-z, 0-9)."
}

$ResourceGroup = "photo-album-resources-$ResourceSuffix"
$AcrName = "photoalbumacr$ResourceSuffix"
$ManagedIdentityName = "photo-album-id-$ResourceSuffix"
$KeyVaultName = "photoalbumkv$ResourceSuffix"
$ContainerAppEnvName = "photo-album-env-$ResourceSuffix"
$ContainerAppName = "photo-album-app"
$SubscriptionId = (az account show --query id --output tsv 2>$null).ToString().Trim()
$ShortSubId = $SubscriptionId.Replace("-", "").Substring(0, 8)
$PostgresServerName = "photoalbum-postgres-$ResourceSuffix-$ShortSubId"
$PostgresAdminUser = "photoalbum_admin"
$PostgresAdminPassword = "P@ssw0rd123!"
$PostgresDatabaseName = "photoalbum"

Write-Info "Using suffix: $ResourceSuffix"
Write-Info "Using location: $Location"

Write-Info "Using default subscription..."
az account show --query "{Name:name, SubscriptionId:id}" -o table

# Built-in role IDs used for RBAC assignments
$AcrPullRoleId = "7f951dda-4ed3-4680-a7ca-43fe172d538d"
$KeyVaultSecretsUserRoleId = "4633458b-17de-408a-b874-0445c86b69e6"
$KeyVaultSecretsOfficerRoleId = "b86a8fe4-44ce-4948-aee5-eccb2c155cd7"

# Create Resource Group
Write-Info "Creating resource group: $ResourceGroup"
az group create --name $ResourceGroup --location $Location

# Create PostgreSQL Flexible Server
Write-Info "Creating PostgreSQL server: $PostgresServerName"
az postgres flexible-server create `
    --resource-group $ResourceGroup `
    --name $PostgresServerName `
    --location $Location `
    --admin-user $PostgresAdminUser `
    --admin-password $PostgresAdminPassword `
    --version "15" `
    --tier "Burstable" `
    --sku-name "Standard_B1ms" `
    --storage-size "32" `
    --backup-retention "7" `
    --public-access "0.0.0.0" `
    --output none

Write-Info "PostgreSQL server created successfully!"

# Create application database
Write-Info "Creating database: $PostgresDatabaseName"
az postgres flexible-server db create `
    --resource-group $ResourceGroup `
    --server-name $PostgresServerName `
    --database-name $PostgresDatabaseName `
    --output none

# Configure firewall for Azure services
Write-Info "Configuring firewall rules..."
az postgres flexible-server firewall-rule create `
    --resource-group $ResourceGroup `
    --name $PostgresServerName `
    --rule-name "AllowAzureServices" `
    --start-ip-address "0.0.0.0" `
    --end-ip-address "0.0.0.0" `
    --output none

# Get server FQDN
Write-Info "Getting server connection details..."
$ServerFqdn = (az postgres flexible-server show `
    --resource-group $ResourceGroup `
    --name $PostgresServerName `
    --query "fullyQualifiedDomainName" `
    --output tsv).ToString().Trim()

if ([string]::IsNullOrWhiteSpace($ServerFqdn)) {
    throw "Could not get PostgreSQL server FQDN from Azure CLI. The server may still be provisioning, or the CLI command failed. Run: az postgres flexible-server show --resource-group $ResourceGroup --name $PostgresServerName --query fullyQualifiedDomainName -o tsv"
}

# Build the connection string. Use ${...} to avoid PowerShell parsing ":" as scoped variable syntax.
$DatasourceUrl = "jdbc:postgresql://${ServerFqdn}:5432/${PostgresDatabaseName}"
Write-Info "Datasource URL: $DatasourceUrl"

# Create Azure Container Registry
Write-Info "Creating Azure Container Registry: $AcrName"
az acr create `
    --name $AcrName `
    --resource-group $ResourceGroup `
    --location $Location `
    --sku Basic `
    --admin-enabled false

Write-Info "Getting Azure Container Registry ID..."
$AcrId = (az acr show `
    --name $AcrName `
    --resource-group $ResourceGroup `
    --query "id" `
    --output tsv).ToString().Trim()

# Create User-Assigned Managed Identity for Container App runtime
Write-Info "Creating managed identity: $ManagedIdentityName"
$ManagedIdentity = az identity create `
    --name $ManagedIdentityName `
    --resource-group $ResourceGroup `
    --location $Location `
    --output json | ConvertFrom-Json

$ManagedIdentityId = $ManagedIdentity.id
$ManagedIdentityPrincipalId = $ManagedIdentity.principalId
$ManagedIdentityClientId = $ManagedIdentity.clientId

if ([string]::IsNullOrWhiteSpace($ManagedIdentityId) -or [string]::IsNullOrWhiteSpace($ManagedIdentityPrincipalId)) {
    throw "Managed identity was created, but required identity fields were empty."
}

# Assign AcrPull role to managed identity (for future image pulls from ACR)
Write-Info "Assigning AcrPull role to managed identity..."
az role assignment create `
    --assignee-object-id $ManagedIdentityPrincipalId `
    --assignee-principal-type ServicePrincipal `
    --scope $AcrId `
    --role $AcrPullRoleId `
    --output none

# Create Key Vault with RBAC enabled
Write-Info "Creating Azure Key Vault: $KeyVaultName"
az keyvault create `
    --name $KeyVaultName `
    --resource-group $ResourceGroup `
    --location $Location `
    --enable-rbac-authorization true `
    --public-network-access Enabled `
    --output none

Write-Info "Getting Key Vault ID..."
$KeyVaultId = (az keyvault show `
    --name $KeyVaultName `
    --resource-group $ResourceGroup `
    --query "id" `
    --output tsv).ToString().Trim()

# Allow managed identity to read Key Vault secrets at runtime
Write-Info "Assigning Key Vault Secrets User role to managed identity..."
az role assignment create `
    --assignee-object-id $ManagedIdentityPrincipalId `
    --assignee-principal-type ServicePrincipal `
    --scope $KeyVaultId `
    --role $KeyVaultSecretsUserRoleId `
    --output none

# Ensure the current deployment principal can write secrets in RBAC mode
$CurrentPrincipalObjectId = ""
try {
    $CurrentPrincipalObjectId = (az ad signed-in-user show --query id --output tsv 2>$null).ToString().Trim()
}
catch {
    $CurrentPrincipalObjectId = ""
}

if ([string]::IsNullOrWhiteSpace($CurrentPrincipalObjectId)) {
    $CurrentAccountName = (az account show --query "user.name" --output tsv).ToString().Trim()
    if (-not [string]::IsNullOrWhiteSpace($CurrentAccountName)) {
        try {
            $CurrentPrincipalObjectId = (az ad sp show --id $CurrentAccountName --query id --output tsv 2>$null).ToString().Trim()
        }
        catch {
            $CurrentPrincipalObjectId = ""
        }
    }
}

if (-not [string]::IsNullOrWhiteSpace($CurrentPrincipalObjectId)) {
    Write-Info "Assigning Key Vault Secrets Officer role to current principal..."
    az role assignment create `
        --assignee-object-id $CurrentPrincipalObjectId `
        --scope $KeyVaultId `
        --role $KeyVaultSecretsOfficerRoleId `
        --output none
}
else {
    Write-WarningMessage "Could not resolve current principal object ID. If secret writes fail, assign yourself the Key Vault Secrets Officer role on $KeyVaultName and re-run."
}

Write-Info "Waiting for role assignments to propagate..."
Start-Sleep -Seconds 30

# Store DB secrets in Key Vault
Write-Info "Storing PostgreSQL secrets in Key Vault..."
az keyvault secret set --vault-name $KeyVaultName --name "pg-connection-url" --value $DatasourceUrl --output none
az keyvault secret set --vault-name $KeyVaultName --name "pg-admin-user" --value $PostgresAdminUser --output none
az keyvault secret set --vault-name $KeyVaultName --name "pg-admin-password" --value $PostgresAdminPassword --output none

$PgUrlSecretId = (az keyvault secret show --vault-name $KeyVaultName --name "pg-connection-url" --query "id" --output tsv).ToString().Trim()
$PgUserSecretId = (az keyvault secret show --vault-name $KeyVaultName --name "pg-admin-user" --query "id" --output tsv).ToString().Trim()
$PgPasswordSecretId = (az keyvault secret show --vault-name $KeyVaultName --name "pg-admin-password" --query "id" --output tsv).ToString().Trim()

if ([string]::IsNullOrWhiteSpace($PgUrlSecretId) -or [string]::IsNullOrWhiteSpace($PgUserSecretId) -or [string]::IsNullOrWhiteSpace($PgPasswordSecretId)) {
    throw "Failed to resolve one or more Key Vault secret IDs."
}

# Create Azure Container Apps Environment
Write-Info "Creating Azure Container Apps Environment: $ContainerAppEnvName"
az containerapp env create `
    --name $ContainerAppEnvName `
    --resource-group $ResourceGroup `
    --location $Location `
    --output none

# Create Azure Container App with a placeholder image and PostgreSQL connection string
Write-Info "Creating Azure Container App: $ContainerAppName"
az containerapp create `
    --name $ContainerAppName `
    --resource-group $ResourceGroup `
    --environment $ContainerAppEnvName `
    --user-assigned $ManagedIdentityId `
    --image "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest" `
    --target-port 80 `
    --ingress external `
    --min-replicas 1 `
    --max-replicas 3 `
    --cpu 0.25 `
    --memory 0.5Gi `
    --secrets `
        "pg-connection-url=keyvaultref:$PgUrlSecretId,identityref:$ManagedIdentityId" `
        "pg-admin-user=keyvaultref:$PgUserSecretId,identityref:$ManagedIdentityId" `
        "pg-admin-password=keyvaultref:$PgPasswordSecretId,identityref:$ManagedIdentityId" `
    --env-vars `
        "SPRING_DATASOURCE_URL=secretref:pg-connection-url" `
        "SPRING_DATASOURCE_USERNAME=secretref:pg-admin-user" `
        "SPRING_DATASOURCE_PASSWORD=secretref:pg-admin-password" `
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver" `
        "SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect" `
        "SPRING_JPA_HIBERNATE_DDL_AUTO=create" `
    --output none

# Get the Container App URL
Write-Info "Getting Container App URL..."
$ContainerAppUrl = az containerapp show `
    --name $ContainerAppName `
    --resource-group $ResourceGroup `
    --query "properties.configuration.ingress.fqdn" `
    --output tsv

# Output connection information
Write-Host ""
Write-Host "================================================================"
Write-Host "Setup Complete!"
Write-Host "================================================================"
Write-Host "Server FQDN: $ServerFqdn"
Write-Host "Database: $PostgresDatabaseName"
Write-Host "Database User: $PostgresAdminUser"
Write-Host "Resource Group: $ResourceGroup"
Write-Host "Key Vault Name: $KeyVaultName"
Write-Host "Managed Identity Client ID: $ManagedIdentityClientId"
Write-Host "Container App URL: https://$ContainerAppUrl"
Write-Host ""
Write-Host "The container app is configured to read DB secrets from Key Vault."
Write-Host ""
Write-Host "To view application logs:"
Write-Host "  az containerapp logs show --name $ContainerAppName --resource-group $ResourceGroup --follow"
Write-Host "================================================================"

Write-WarningMessage "Please save these credentials securely!"

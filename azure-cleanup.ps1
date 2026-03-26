param(
    [Parameter(Mandatory = $true)]
    [string]$Suffix
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

Write-Info "=== Azure Photo Album Teardown ==="

$ResourceSuffix = $Suffix.ToLowerInvariant()
$ResourceGroup = "photo-album-resources-$ResourceSuffix"
$KeyVaultName = "photoalbumkv$ResourceSuffix"

Write-Info "Target resource group: $ResourceGroup"

# Check if resource group exists
$RgExists = "$(az group exists --name $ResourceGroup)".Trim()
if ($RgExists -ne "true") {
    Write-WarningMessage "Resource group '$ResourceGroup' does not exist. Nothing to delete."
}
else {
    # Purge Key Vault before deleting the resource group to avoid soft-delete conflicts on re-creation
    $KvExists = "$(az keyvault show --name $KeyVaultName --resource-group $ResourceGroup --query "name" --output tsv 2>$null)".Trim()
    if (-not [string]::IsNullOrWhiteSpace($KvExists)) {
        Write-Info "Deleting Key Vault: $KeyVaultName (will purge to avoid soft-delete conflict)..."
        az keyvault delete --name $KeyVaultName --resource-group $ResourceGroup --output none
        az keyvault purge --name $KeyVaultName --output none
        Write-Info "Key Vault purged."
    }

    # Delete the entire resource group (removes all other resources: ACR, Postgres, Container App, Identity)
    Write-Info "Deleting resource group '$ResourceGroup' and all its resources..."
    az group delete --name $ResourceGroup --yes --no-wait --output none
    Write-Info "Resource group deletion initiated (running in background on Azure)."
}

# Check and purge any soft-deleted Key Vault with this name (from a previous run without purge)
Write-Info "Checking for soft-deleted Key Vault: $KeyVaultName..."
$SoftDeleted = "$(az keyvault list-deleted --query "[?name=='$KeyVaultName'].name" --output tsv 2>$null)".Trim()
if (-not [string]::IsNullOrWhiteSpace($SoftDeleted)) {
    Write-Info "Found soft-deleted Key Vault '$KeyVaultName'. Purging..."
    az keyvault purge --name $KeyVaultName --output none
    Write-Info "Soft-deleted Key Vault purged."
}
else {
    Write-Info "No soft-deleted Key Vault found for '$KeyVaultName'."
}

Write-Info "Teardown complete for suffix: $ResourceSuffix"
Write-WarningMessage "Resource group deletion runs asynchronously on Azure. Wait a minute before re-running setup."

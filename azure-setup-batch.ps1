$ErrorActionPreference = "Stop"
$ScriptPath = Join-Path $PSScriptRoot "azure-setup.ps1"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Ensure-ProviderRegistered {
    param([Parameter(Mandatory = $true)][string]$Namespace)
    $state = (az provider show --namespace $Namespace --query registrationState --output tsv 2>$null).ToString().Trim()
    if ($state -eq "Registered") {
        Write-Info "Provider already registered: $Namespace"
        return
    }
    Write-Info "Registering provider: $Namespace"
    az provider register --namespace $Namespace --wait --output none
    $finalState = (az provider show --namespace $Namespace --query registrationState --output tsv).ToString().Trim()
    if ($finalState -ne "Registered") {
        throw "Failed to register provider namespace '$Namespace'. Current state: $finalState"
    }
    Write-Info "Provider registered: $Namespace"
}

# === PRE-FLIGHT CHECKS (run once before parallel jobs) ===
Write-Info "=== Pre-flight: CLI extensions and provider registration ==="

# Install/upgrade containerapp extension ONCE
Write-Info "Ensuring containerapp CLI extension is installed..."
az extension add --allow-preview --upgrade --yes --name containerapp

# Register providers ONCE
$RequiredProviders = @(
    "Microsoft.App",
    "Microsoft.ContainerRegistry",
    "Microsoft.DBforPostgreSQL",
    "Microsoft.KeyVault",
    "Microsoft.ManagedIdentity"
)
foreach ($ProviderNamespace in $RequiredProviders) {
    Ensure-ProviderRegistered -Namespace $ProviderNamespace
}

Write-Info "Pre-flight complete. Starting jobs..."

$Configurations = @(
    @{ Suffix = "001"; Location = "Japan West" },
    @{ Suffix = "002"; Location = "Japan West" },
    @{ Suffix = "003"; Location = "Japan West" },
    @{ Suffix = "004"; Location = "Japan West" },
    @{ Suffix = "005"; Location = "Japan West" },
    @{ Suffix = "006"; Location = "Japan West" },
    @{ Suffix = "007"; Location = "Japan West" },
    @{ Suffix = "008"; Location = "Japan West" },
    @{ Suffix = "009"; Location = "Japan West" },
    @{ Suffix = "010"; Location = "Japan West" }
)

Write-Host "Starting $($Configurations.Count) jobs..."

# $Configurations | ForEach-Object -Parallel {
#     pwsh -ExecutionPolicy Bypass -File $using:ScriptPath -Suffix $_.Suffix -Location $_.Location
# } -ThrottleLimit 10

foreach ($Config in $Configurations) {
    pwsh -ExecutionPolicy Bypass -File $ScriptPath -Suffix $Config.Suffix -Location $Config.Location
}

Write-Host "`nAll jobs finished."

$ScriptPath = Join-Path $PSScriptRoot "azure-setup.ps1"

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

Write-Host "Starting $($Configurations.Count) parallel jobs..."

$Configurations | ForEach-Object -Parallel {
    powershell -ExecutionPolicy Bypass -File $using:ScriptPath -Suffix $_.Suffix -Location $_.Location
} -ThrottleLimit 10

Write-Host "`nAll jobs finished."

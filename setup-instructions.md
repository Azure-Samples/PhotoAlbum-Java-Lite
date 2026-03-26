# Prerequisite
1. AZ CLI installed
1. Powershell version 7. Check with `$PSVersionTable.PSVersion`. Upgrade with `winget install Microsoft.PowerShell`.
1. Account should have **Owner** role or alternatively Contributor + User Access Administrator role.

# Infra Setup
1. Login in to the right subscription.
   ```
   az login
   az account show
   ```
1. Navigate to the right folder. 
1. Run the [azure-setup-batch.ps1](https://github.com/Azure-Samples/PhotoAlbum-Java-Lite/blob/provision-resources/azure-setup-batch.ps1) to provision 10 resources in Japan West region.
  To change the resources' suffixes and region, update the configurations in the script.
   ```powershell
   pwsh -ExecutionPolicy Bypass -File .\azure-setup-batch.ps1
   ```
   The script uses [azure-setup.ps1](https://github.com/Azure-Samples/PhotoAlbum-Java-Lite/blob/provision-resources/azure-setup.ps1) to set up all the infrastructure. 
1. The provisioning runs one resource group at a time. It's recommended to fix errors after all the jobs are finished.
1. View the terminal output and see whether there are failed resources. Delete the entire resource group with [azure-cleanup.ps1](https://github.com/Azure-Samples/PhotoAlbum-Java-Lite/blob/provision-resources/azure-cleanup.ps1).
   ```powershell
   pwsh -ExecutionPolicy Bypass -File .\azure-cleanup.ps1 -Suffix <the-failed-suffix>
   ```
   To do this manually via Portal, the deletion should include the removal of the resource group and the **purge** of the key vault to avoid name conflicts during re-provisioning later.
   Provision one resource group with [azure-setup.ps1](https://github.com/Azure-Samples/PhotoAlbum-Java-Lite/blob/provision-resources/azure-setup.ps1).
   ```powershell
   pwsh -ExecutionPolicy Bypass -File .\azure-setup.ps1 -Suffix <Suffix> -Location <region>
   ```

# Photo Album Migration Workshop - Cloud Storage Preparation Checklist

This document lists all the software, tools, binaries, and repositories needed for the workshop, along with download links and collection methods. This is for workshop organizers to prepare materials on cloud storage.

**Estimated Total Size**: ~500-600 MB (Windows only)

---

## 📁 Folder Structure on Cloud Drive

```
CLOUD-DRIVE/
├── 1-Installers/
│   ├── JDK/
│   ├── Maven/
│   ├── Git/
│   ├── VSCode/
│   ├── AzureCLI/
│   │   └── extensions/
│   └── AppCAT/
├── 2-VSCode-Extensions/
└── 3-Repository/
```

---

## 1️⃣ Required Software Installers

### **Java Development Kit (JDK)**
- **Download**: https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.msi
- **File**: `microsoft-jdk-21-windows-x64.msi`
- **Size**: ~200 MB
- **Storage**: `1-Installers/JDK/`

### **Apache Maven**
- **Download**: https://maven.apache.org/download.cgi
- **File**: `apache-maven-3.9.x-bin.zip`
- **Size**: ~10 MB
- **Storage**: `1-Installers/Maven/`

### **Git**
- **Download**: https://git-scm.com/downloads
- **File**: `Git-2.xx.x-64-bit.exe`
- **Size**: ~50 MB
- **Storage**: `1-Installers/Git/`

### **Visual Studio Code**
- **Download**: https://code.visualstudio.com/download
- **File**: `VSCodeUserSetup-x64-1.xx.x.exe` (version 1.101 or later)
- **Size**: ~100-150 MB
- **Storage**: `1-Installers/VSCode/`

### **Azure CLI**
- **Download**: https://aka.ms/installazurecliwindowsx64
- **File**: `azure-cli-2.xx.x.msi`
- **Size**: ~100 MB
- **Storage**: `1-Installers/AzureCLI/`

### **Azure CLI Container Apps Extension**
- **How to collect**:
  1. Install Azure CLI on your machine
  2. Run: `az extension add --allow-preview --upgrade --yes --name containerapp`
  3. Locate the `.whl` file at: `C:\Users\<YourUsername>\.azure\cliextensions\containerapp\`
  4. Copy `containerapp-<version>-py3-none-any.whl` to cloud storage

  *If not found, use: `az extension show --name containerapp --query "path" -o tsv`*

- **File**: `containerapp-<version>-py3-none-any.whl`
- **Size**: ~5-10 MB
- **Storage**: `1-Installers/AzureCLI/extensions/`

### **AppCAT CLI**
- **Download**: https://aka.ms/appcat-install
- **File**: `azure-migrate-appcat-for-java-cli-windows-amd64-x.x.x.x.zip`
- **Size**: ~50-100 MB
- **Storage**: `1-Installers/AppCAT/`

---

## 2️⃣ Visual Studio Code Extensions

**How to download .vsix files**:
* Try Option 1: On each extension's marketplace page, click "Download Extension" link on the right sidebar, or click the "..." menu and select "Download Extension".
* Try Option 2: In VS Code, open the extensions panel, search for the extension, right click on the extension in the list, and click "Download VSIX".

1. **GitHub Copilot Chat**
   - **Download**: https://marketplace.visualstudio.com/items?itemName=GitHub.copilot-chat
   - **File**: `GitHub.copilot-chat-x.x.x.vsix`
   - **Size**: ~2 MB

2. **GitHub Copilot modernization**
   - **Download**: https://marketplace.visualstudio.com/items?itemName=vscjava.migrate-java-to-azure
   - **File**: `vscjava.migrate-java-to-azure-x.x.x.vsix`
   - **Size**: ~10 MB

3. **Bicep**
   - **Download**: https://marketplace.visualstudio.com/items?itemName=ms-azuretools.vscode-bicep
   - **File**: `ms-azuretools.vscode-bicep-x.x.x.vsix`
   - **Size**: ~5 MB

**Storage**: `2-VSCode-Extensions/`

---

## 3️⃣ Git Repository

### **PhotoAlbum-Java-Lite**
- **Repository**: https://github.com/Azure-Samples/PhotoAlbum-Java-Lite
- **Clone command**: `git clone https://github.com/Azure-Samples/PhotoAlbum-Java-Lite.git`
- **Alternative**: Download ZIP from GitHub by clicking the green "&lt;&gt; Code" button and then "Download ZIP".
- **Size**: ~5-10 MB
- **Storage**: `3-Repository/PhotoAlbum-Java-Lite/`

---

## 4️⃣ Azure Subscription Preparation

For organizers preparing Azure subscriptions for participants, ensure the following resource providers are registered and available.

### **Required Resource Providers**

1. `Microsoft.App` - For Azure Container Apps
2. `Microsoft.OperationalInsights` - For Log Analytics workspace
3. `Microsoft.DBforPostgreSQL` - For Azure Database for PostgreSQL
4. `Microsoft.ManagedIdentity` - For managed identity authentication
5. `Microsoft.ContainerRegistry` - For Azure Container Registry
6. `Microsoft.Insights` - For Application Insights monitoring
7. `Microsoft.Network` - For networking resources
8. `Microsoft.Resources` - For resource management
9. `Microsoft.KeyVault` - For Azure Key Vault

### **How to Register Providers**

**Using Azure Portal:**
1. Navigate to Subscriptions → Select subscription → Resource providers
2. Search for each provider and click "Register" if not already registered

**Using Azure CLI:**
```bash
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.ManagedIdentity
az provider register --namespace Microsoft.ContainerRegistry
az provider register --namespace Microsoft.Insights
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.Resources
az provider register --namespace Microsoft.KeyVault
```

It takes some time to get a resource provider registered in a subscription. Use the following command to check the registration status:

```bash
# replace the <namespace> part with the namespace you passed in the above register command
az provider show --namespace <namespace> --query registrationState --output tsv
#=> Registered
```

**Reference**: https://learn.microsoft.com/azure/azure-resource-manager/management/resource-providers-and-types

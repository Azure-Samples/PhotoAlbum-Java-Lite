# Photo Album Migration Workshop - Setup Guide for Participants

This guide will help you download and install all the prerequisites needed for the workshop from the provided cloud storage.

**Estimated Setup Time**: 30-45 minutes

---

## 📋 Prerequisites

- Windows 10/11 (x64)
- Administrator access to install software
- Internet connection (for accessing cloud storage)
- Access to the workshop cloud drive (link provided by organizers)

---

## 🚀 Installation Steps

### Step 1: Access Cloud Storage

1. Open the cloud drive link provided by your workshop organizers
2. You should see the following folder structure:
   ```
   CLOUD-DRIVE/
   ├── 1-Installers/
   ├── 2-VSCode-Extensions/
   └── 3-Repository/
   ```

### Step 2: Install Java Development Kit (JDK)

1. Navigate to `1-Installers/JDK/`
2. Download `microsoft-jdk-21-windows-x64.msi`
3. Run the installer and follow the installation wizard
4. Keep all default settings
5. **Set JAVA_HOME environment variable**:
   - Open System Properties → Environment Variables
   - Under System Variables, click New
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Microsoft\jdk-21.0.x` (replace x with your actual version)
   - Click OK
   - Find `Path` in System Variables and click Edit
   - Click New and add: `%JAVA_HOME%\bin`
   - Click OK on all dialogs
6. **Verify installation** (open a new PowerShell window):
   ```powershell
   java -version
   $env:JAVA_HOME
   ```
   You should see: `openjdk version "21.x.x"` and the JAVA_HOME path

### Step 3: Install Apache Maven

1. Navigate to `1-Installers/Maven/`
2. Download `apache-maven-3.9.9-bin.zip`
3. Extract the ZIP file to `C:\Program Files\Apache\`
4. **Add Maven to PATH**:
   - Open System Properties → Environment Variables
   - Under System Variables, find `Path` and click Edit
   - Click New and add: `C:\Program Files\Apache\apache-maven-3.9.9\bin`
   - Click OK on all dialogs
5. **Verify installation** (open a new PowerShell window):
   ```powershell
   mvn -version
   ```

### Step 4: Install Git

1. Navigate to `1-Installers/Git/`
2. Download `Git-2.xx.x-64-bit.exe`
3. Run the installer
4. Keep all default settings during installation
5. **Verify installation**:
   ```powershell
   git --version
   ```

### Step 5: Install Visual Studio Code

1. Navigate to `1-Installers/VSCode/`
2. Download `VSCodeUserSetup-x64-1.xx.x.exe`
3. Run the installer
4. **Recommended options during installation**:
   - ✅ Add "Open with Code" action to Windows Explorer context menu
   - ✅ Add to PATH
5. **Verify installation**: Launch VS Code from Start menu or run `code` in PowerShell

### Step 6: Install Azure CLI

1. Navigate to `1-Installers/AzureCLI/`
2. Download `azure-cli-2.xx.x.msi`
3. Run the installer and follow the installation wizard
4. **Verify installation** (open a new PowerShell window):
   ```powershell
   az --version
   ```
5. **Login to Azure**:
   ```powershell
   az login
   ```
   - This will open a browser window for authentication
   - Sign in with the Azure account provided by workshop organizers
   - After successful login, you'll see a list of subscriptions in the terminal

6. **Set the workshop subscription**:
   - If you have multiple subscriptions, set the one provided by organizers:
   ```powershell
   az account list --output table
   ```
   - Find the subscription name or ID provided by organizers
   - Set it as default:
   ```powershell
   az account set --subscription "Your-Subscription-Name-or-ID"
   ```
   - Verify the correct subscription is selected:
   ```powershell
   az account show --output table
   ```

### Step 7: Install Azure CLI Container Apps Extension

1. Navigate to `1-Installers/AzureCLI/extensions/`
2. Download the `containerapp-<version>-py3-none-any.whl` file to a temporary location (e.g., Downloads folder)
3. Open PowerShell and run:
   ```powershell
   az extension add --source C:\Users\<YourUsername>\Downloads\containerapp-<version>-py3-none-any.whl
   ```
   Replace `<YourUsername>` and `<version>` with actual values
4. **Verify installation**:
   ```powershell
   az extension list --output table
   ```
   You should see `containerapp` in the list

### Step 8: Install AppCAT CLI

1. Navigate to `1-Installers/AppCAT/`
2. Download `azure-migrate-appcat-for-java-cli-windows-amd64-x.x.x.x.zip`
3. Extract the ZIP file to `C:\Users\<YourUsername>\.appcat\`
   - Replace `<YourUsername>` with your actual Windows username
   - Create the `.appcat` folder if it doesn't exist
4. **Add AppCAT to PATH**:
   - Open System Properties → Environment Variables
   - Under System Variables, find `Path` and click Edit
   - Click New and add: `C:\Users\<YourUsername>\.appcat`
   - Click OK on all dialogs
5. **Verify installation** (open a new PowerShell window):
   ```powershell
   appcat version
   ```

### Step 9: Install VS Code Extensions

1. Navigate to `2-VSCode-Extensions/`
2. Download all four `.vsix` files:
   - `GitHub.copilot-x.x.x.vsix`
   - `GitHub.copilot-chat-x.x.x.vsix`
   - `vscjava.migrate-java-to-azure-x.x.x.vsix`
   - `ms-azuretools.vscode-bicep-x.x.x.vsix`
3. Open VS Code
4. For each `.vsix` file:
   - Open Command Palette (`Ctrl+Shift+P`)
   - Type: `Extensions: Install from VSIX`
   - Select the downloaded `.vsix` file
   - Wait for installation to complete
5. **Verify installation**:
   - Click the Extensions icon in VS Code sidebar
   - You should see all four extensions installed

**Alternative method** (PowerShell):
```powershell
code --install-extension C:\path\to\GitHub.copilot-x.x.x.vsix
code --install-extension C:\path\to\GitHub.copilot-chat-x.x.x.vsix
code --install-extension C:\path\to\vscjava.migrate-java-to-azure-x.x.x.vsix
code --install-extension C:\path\to\ms-azuretools.vscode-bicep-x.x.x.vsix
```

### Step 10: Get Workshop Repository

1. Navigate to `3-Repository/`
2. **Option A - Copy from cloud storage**:
   - Copy the entire `PhotoAlbum-Java-Lite` folder to your local workspace (e.g., `C:\Workshops\`)

3. **Option B - Clone from GitHub** (if internet available):
   ```powershell
   git clone https://github.com/Azure-Samples/PhotoAlbum-Java-Lite.git
   ```

4. Open the project in VS Code:
   ```powershell
   cd PhotoAlbum-Java-Lite
   code .
   ```

### Step 11: Configure Maven with Aliyun Mirror

To speed up Maven dependency downloads, configure the Aliyun Maven mirror:

1. Open your Maven settings file:
   - Location: `C:\Users\<YourUsername>\.m2\settings.xml`
   - If the file doesn't exist, create it

2. Add the following content to `settings.xml`:
   ```xml
   <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                                 http://maven.apache.org/xsd/settings-1.0.0.xsd">
     <mirrors>
       <mirror>
         <id>aliyun-maven</id>
         <mirrorOf>central</mirrorOf>
         <name>Aliyun Maven Mirror</name>
         <url>https://maven.aliyun.com/repository/public</url>
       </mirror>
       <mirror>
         <id>aliyun-spring</id>
         <mirrorOf>spring-releases,spring-milestones,spring-snapshots</mirrorOf>
         <name>Aliyun Spring Maven Mirror</name>
         <url>https://maven.aliyun.com/repository/spring</url>
       </mirror>
     </mirrors>
   </settings>
   ```

3. **Verify configuration**:
   ```powershell
   cd PhotoAlbum-Java-Lite
   mvn dependency:resolve
   ```
   You should see Maven downloading dependencies from `maven.aliyun.com`

**Note**: The Aliyun mirrors provide faster access to both Maven Central and Spring repositories, especially in regions with slower connections to these repositories.

---

## ✅ Verification Checklist

Before the workshop starts, verify all installations:

- [ ] `java -version` shows Java 21
- [ ] `mvn -version` shows Maven 3.9.x
- [ ] `git --version` shows Git version
- [ ] `code --version` shows VS Code version
- [ ] `az --version` shows Azure CLI version
- [ ] `az account show` shows correct subscription (provided by organizers)
- [ ] `az extension list` shows containerapp extension
- [ ] `appcat version` shows AppCAT CLI version
- [ ] VS Code has all 4 extensions installed
- [ ] PhotoAlbum-Java-Lite project is accessible
- [ ] Maven can resolve dependencies (run `mvn dependency:resolve` in the project folder)

---

## 🔑 Azure Account Setup

**Note**: Azure login and subscription setup are already covered in Step 6 above. This section provides additional information.

### Understanding Your Azure Subscription

Your workshop organizers have provided you with:
- **Azure account credentials** (username/email)
- **Subscription name or ID** - Make sure you've set this as your default subscription

### Verify Your Access

After completing Step 6, verify you have access to the required Azure resources:

```powershell
# Check your current subscription
az account show --output table

# Verify resource providers are registered (these should already be set up by organizers)
az provider list --query "[?registrationState=='Registered'].namespace" --output table
```

You should see the following providers registered:
- `Microsoft.App`
- `Microsoft.ContainerRegistry`
- `Microsoft.DBforPostgreSQL`
- `Microsoft.Insights`
- `Microsoft.KeyVault`
- `Microsoft.ManagedIdentity`
- `Microsoft.Network`
- `Microsoft.OperationalInsights`
- `Microsoft.Resources`

### If You Don't Have Azure Account Credentials

Contact your workshop organizers for:
1. Azure account credentials
2. The specific subscription name or ID to use
3. Any additional access permissions needed

package com.photoalbum.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Azure Blob Storage client.
 * Uses a connection string when {@code azure.storage.blob.connection-string} is set (e.g. for
 * local testing with Azurite), and falls back to Managed Identity (DefaultAzureCredential)
 * for production deployments.
 */
@Configuration
public class BlobStorageConfig {

    @Value("${azure.storage.blob.account-name}")
    private String accountName;

    @Value("${azure.storage.blob.container-name}")
    private String containerName;

    @Value("${azure.storage.blob.connection-string:}")
    private String connectionString;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (connectionString != null && !connectionString.isEmpty()) {
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
        }
        String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }
}

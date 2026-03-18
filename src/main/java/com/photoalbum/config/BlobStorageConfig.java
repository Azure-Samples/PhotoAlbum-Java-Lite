package com.photoalbum.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Azure Blob Storage client using Managed Identity (DefaultAzureCredential).
 */
@Configuration
public class BlobStorageConfig {

    @Value("${azure.storage.blob.account-name}")
    private String accountName;

    @Value("${azure.storage.blob.container-name}")
    private String containerName;

    @Bean
    public BlobServiceClient blobServiceClient() {
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

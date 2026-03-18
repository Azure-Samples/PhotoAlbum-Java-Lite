package com.photoalbum;

import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PhotoAlbumApplicationTests {

    @MockBean
    BlobContainerClient blobContainerClient;

    @Test
    void contextLoads() {
        // This test ensures that the Spring context loads correctly
    }
}
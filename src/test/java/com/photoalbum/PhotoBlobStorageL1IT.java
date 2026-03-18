package com.photoalbum;

import com.azure.storage.blob.BlobContainerClient;
import com.photoalbum.model.Photo;
import com.photoalbum.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Layer 1 Integration Tests for Azure Blob Storage migration.
 * Uses Azurite emulator via TestContainers and H2 (Oracle mode) for the database.
 * Tests the full stack: controller → service → Azure Blob Storage (Azurite) + DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("layer1it")
@Tag("Layer1")
@Testcontainers
class PhotoBlobStorageL1IT {

    @Container
    static GenericContainer<?> azurite = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
            .withExposedPorts(10000)
            .withCommand("azurite-blob --blobHost 0.0.0.0 --loose")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void configureAzuriteProperties(DynamicPropertyRegistry registry) {
        String connectionString = String.format(
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
                "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
                "BlobEndpoint=http://%s:%d/devstoreaccount1;",
                azurite.getHost(), azurite.getMappedPort(10000));
        registry.add("azure.storage.blob.connection-string", () -> connectionString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private BlobContainerClient blobContainerClient;

    private static final byte[] TEST_JPEG_BYTES;
    private static final byte[] TEST_PNG_BYTES;

    static {
        try {
            TEST_JPEG_BYTES = createTestImage(100, 100, "JPEG");
            TEST_PNG_BYTES  = createTestImage(50, 50, "PNG");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test images", e);
        }
    }

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        blobContainerClient.listBlobs()
                .forEach(item -> blobContainerClient.getBlobClient(item.getName()).deleteIfExists());
    }

    // -------------------------------------------------------------------------
    // Happy-path upload tests
    // -------------------------------------------------------------------------

    @Test
    void uploadPhoto_withValidJpeg_storesInAzuriteAndSavesUrlInDatabase() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", TEST_JPEG_BYTES);

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uploadedPhotos", hasSize(1)))
                .andExpect(jsonPath("$.uploadedPhotos[0].originalFileName").value("photo.jpg"))
                .andExpect(jsonPath("$.uploadedPhotos[0].id").isNotEmpty());

        List<Photo> photos = photoRepository.findAll();
        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).getBlobUrl())
                .as("Blob URL should point to Azurite devstoreaccount1")
                .contains("devstoreaccount1");

        // Verify the blob actually exists in Azurite
        String blobName = extractBlobName(photos.get(0).getBlobUrl());
        assertThat(blobContainerClient.getBlobClient(blobName).exists())
                .as("Blob must exist in Azurite after upload")
                .isTrue();
    }

    @Test
    void uploadPhoto_withValidPng_storesInAzuriteAndSavesCorrectMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.png", "image/png", TEST_PNG_BYTES);

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uploadedPhotos", hasSize(1)));

        List<Photo> photos = photoRepository.findAll();
        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).getMimeType()).isEqualTo("image/png");
        assertThat(photos.get(0).getBlobUrl()).isNotBlank();

        String blobName = extractBlobName(photos.get(0).getBlobUrl());
        assertThat(blobContainerClient.getBlobClient(blobName).exists()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Validation error tests
    // -------------------------------------------------------------------------

    @Test
    void uploadPhoto_withInvalidFileType_rejectsUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "document.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedUploads", hasSize(1)))
                .andExpect(jsonPath("$.failedUploads[0].error").isNotEmpty());

        assertThat(photoRepository.count()).isZero();
        assertThat(countBlobs()).isZero();
    }

    @Test
    void uploadPhoto_withFileTooLarge_rejectsUpload() throws Exception {
        byte[] largeFile = new byte[11 * 1024 * 1024]; // 11 MB — exceeds 10 MB limit
        MockMultipartFile file = new MockMultipartFile(
                "files", "large.jpg", "image/jpeg", largeFile);

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedUploads", hasSize(1)))
                .andExpect(jsonPath("$.failedUploads[0].error", containsString("limit")));

        assertThat(photoRepository.count()).isZero();
        assertThat(countBlobs()).isZero();
    }

    @Test
    void uploadPhoto_withEmptyFile_rejectsUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedUploads", hasSize(1)));

        assertThat(photoRepository.count()).isZero();
        assertThat(countBlobs()).isZero();
    }

    @Test
    void uploadWithNoFiles_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/upload"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Photo listing tests
    // -------------------------------------------------------------------------

    @Test
    void listPhotos_afterTwoUploads_returnsNewestFirst() throws Exception {
        uploadFileAndGetId("first.jpg");
        Thread.sleep(20);
        uploadFileAndGetId("second.jpg");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        List<Photo> photos = photoRepository.findAllOrderByUploadedAtDesc();
        assertThat(photos).hasSize(2);
        assertThat(photos.get(0).getOriginalFileName())
                .as("Newest photo should appear first")
                .isEqualTo("second.jpg");
    }

    @Test
    void homePageLoadsWithEmptyGallery() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("photos"));
    }

    // -------------------------------------------------------------------------
    // Detail page tests
    // -------------------------------------------------------------------------

    @Test
    void detailPage_existingPhoto_rendersDetailView() throws Exception {
        String id = uploadFileAndGetId("detail-test.jpg");

        mockMvc.perform(get("/detail/" + id))
                .andExpect(status().isOk())
                .andExpect(view().name("detail"))
                .andExpect(model().attributeExists("photo"));
    }

    @Test
    void detailPage_nonExistentPhoto_redirectsToHome() throws Exception {
        mockMvc.perform(get("/detail/nonexistent-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // -------------------------------------------------------------------------
    // Photo serving (blob URL redirect) tests
    // -------------------------------------------------------------------------

    @Test
    void servePhoto_existingPhoto_redirectsToExactBlobUrl() throws Exception {
        String id = uploadFileAndGetId("serve-test.jpg");
        Photo photo = photoRepository.findById(id).orElseThrow();

        mockMvc.perform(get("/photo/" + id))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", photo.getBlobUrl()));
    }

    @Test
    void servePhoto_nonExistentPhoto_returnsNotFound() throws Exception {
        mockMvc.perform(get("/photo/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Photo deletion tests
    // -------------------------------------------------------------------------

    @Test
    void deletePhoto_existingPhoto_removesBlobFromAzuriteAndDatabaseRecord() throws Exception {
        String id = uploadFileAndGetId("to-delete.jpg");
        Photo photo = photoRepository.findById(id).orElseThrow();
        String blobName = extractBlobName(photo.getBlobUrl());

        assertThat(blobContainerClient.getBlobClient(blobName).exists())
                .as("Blob must exist before deletion")
                .isTrue();

        mockMvc.perform(post("/detail/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(photoRepository.findById(id)).isEmpty();
        assertThat(blobContainerClient.getBlobClient(blobName).exists())
                .as("Blob must be deleted from Azurite")
                .isFalse();
    }

    @Test
    void deletePhoto_nonExistentPhoto_redirectsGracefully() throws Exception {
        mockMvc.perform(post("/detail/nonexistent-id/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // -------------------------------------------------------------------------
    // Multi-photo batch upload tests
    // -------------------------------------------------------------------------

    @Test
    void uploadMultiplePhotos_allValid_allStoredInAzurite() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("files", "p1.jpg", "image/jpeg", TEST_JPEG_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "p2.jpg", "image/jpeg", TEST_JPEG_BYTES);
        MockMultipartFile f3 = new MockMultipartFile("files", "p3.jpg", "image/jpeg", TEST_JPEG_BYTES);

        mockMvc.perform(multipart("/upload").file(f1).file(f2).file(f3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uploadedPhotos", hasSize(3)));

        assertThat(photoRepository.count()).isEqualTo(3);
        assertThat(countBlobs()).isEqualTo(3);
    }

    @Test
    void uploadMultiplePhotos_mixedValidAndInvalid_partialSuccess() throws Exception {
        MockMultipartFile valid   = new MockMultipartFile("files", "ok.jpg",  "image/jpeg",      TEST_JPEG_BYTES);
        MockMultipartFile invalid = new MockMultipartFile("files", "bad.txt", "text/plain",       "text".getBytes());

        mockMvc.perform(multipart("/upload").file(valid).file(invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedPhotos", hasSize(1)))
                .andExpect(jsonPath("$.failedUploads", hasSize(1)));

        assertThat(photoRepository.count()).isEqualTo(1);
        assertThat(countBlobs()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Navigation tests
    // -------------------------------------------------------------------------

    @Test
    void navigatePhotos_afterUploadingTwo_detailPageLoadsWithoutCrash() throws Exception {
        String id1 = uploadFileAndGetId("nav-first.jpg");
        Thread.sleep(50);
        String id2 = uploadFileAndGetId("nav-second.jpg");

        // The detail page loads photo data and calls prev/next navigation.
        // With H2 Oracle mode, this exercises the ROWNUM-based navigation queries.
        var result1 = mockMvc.perform(get("/detail/" + id1)).andReturn();
        assertThat(result1.getResponse().getStatus())
                .as("Detail page for first photo should not return 5xx")
                .isLessThan(500);

        var result2 = mockMvc.perform(get("/detail/" + id2)).andReturn();
        assertThat(result2.getResponse().getStatus())
                .as("Detail page for second photo should not return 5xx")
                .isLessThan(500);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Upload a test JPEG file and return the saved photo ID. */
    private String uploadFileAndGetId(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", filename, "image/jpeg", TEST_JPEG_BYTES);
        String json = mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractFirstPhotoId(json);
    }

    /** Parse the first photo ID from the upload JSON response. */
    private String extractFirstPhotoId(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        int end   = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /** Extract the blob name (last path segment) from a blob URL. */
    private String extractBlobName(String blobUrl) {
        return blobUrl.substring(blobUrl.lastIndexOf('/') + 1);
    }

    /** Count the number of blobs currently in the test container. */
    private long countBlobs() {
        List<String> names = new ArrayList<>();
        blobContainerClient.listBlobs().forEach(item -> names.add(item.getName()));
        return names.size();
    }

    /** Create a minimal test image as a byte array. */
    private static byte[] createTestImage(int width, int height, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }
}

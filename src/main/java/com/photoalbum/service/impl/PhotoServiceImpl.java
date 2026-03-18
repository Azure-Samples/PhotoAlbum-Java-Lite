package com.photoalbum.service.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.photoalbum.model.Photo;
import com.photoalbum.model.UploadResult;
import com.photoalbum.repository.PhotoRepository;
import com.photoalbum.service.PhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for photo operations including upload, retrieval, and deletion
 */
@Service
@Transactional
public class PhotoServiceImpl implements PhotoService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final BlobContainerClient blobContainerClient;
    private final long maxFileSizeBytes;
    private final List<String> allowedMimeTypes;

    public PhotoServiceImpl(
            PhotoRepository photoRepository,
            BlobContainerClient blobContainerClient,
            @Value("${app.file-upload.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.file-upload.allowed-mime-types}") String[] allowedMimeTypes) {
        this.photoRepository = photoRepository;
        this.blobContainerClient = blobContainerClient;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedMimeTypes = Arrays.asList(allowedMimeTypes);
    }

    /**
     * Get all photos ordered by upload date (newest first)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Photo> getAllPhotos() {
        try {
            return photoRepository.findAllOrderByUploadedAtDesc();
        } catch (Exception ex) {
            logger.error("Error retrieving photos from database", ex);
            throw new RuntimeException("Error retrieving photos", ex);
        }
    }

    /**
     * Get a specific photo by ID
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getPhotoById(String id) {
        try {
            return photoRepository.findById(id);
        } catch (Exception ex) {
            logger.error("Error retrieving photo with ID {}", id, ex);
            throw new RuntimeException("Error retrieving photo", ex);
        }
    }

    /**
     * Upload a photo file — stores binary in Azure Blob Storage and saves the blob URL in the database
     */
    @Override
    public UploadResult uploadPhoto(MultipartFile file) {
        UploadResult result = new UploadResult();
        result.setFileName(file.getOriginalFilename());

        try {
            // Validate file type
            if (!allowedMimeTypes.contains(file.getContentType().toLowerCase())) {
                result.setSuccess(false);
                result.setErrorMessage("File type not supported. Please upload JPEG, PNG, GIF, or WebP images.");
                logger.warn("Upload rejected: Invalid file type {} for {}",
                    file.getContentType(), file.getOriginalFilename());
                return result;
            }

            // Validate file size
            if (file.getSize() > maxFileSizeBytes) {
                result.setSuccess(false);
                result.setErrorMessage("File size exceeds %dMB limit.".formatted(maxFileSizeBytes / 1024 / 1024));
                logger.warn("Upload rejected: File size {} exceeds limit for {}",
                    file.getSize(), file.getOriginalFilename());
                return result;
            }

            // Validate file not empty
            if (file.getSize() <= 0) {
                result.setSuccess(false);
                result.setErrorMessage("File is empty.");
                return result;
            }

            // Read bytes and extract image dimensions
            byte[] photoBytes;
            Integer width = null;
            Integer height = null;

            try {
                photoBytes = file.getBytes();
                try (ByteArrayInputStream bis = new ByteArrayInputStream(photoBytes)) {
                    BufferedImage image = ImageIO.read(bis);
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                }
            } catch (IOException ex) {
                logger.error("Error reading file data for {}", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error reading file data. Please try again.");
                return result;
            } catch (Exception ex) {
                logger.warn("Could not extract image dimensions for {}", file.getOriginalFilename(), ex);
                photoBytes = file.getBytes();
            }

            // Upload to Azure Blob Storage
            String blobName = java.util.UUID.randomUUID() + "_" + file.getOriginalFilename();
            String blobUrl;
            try {
                BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
                BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(file.getContentType());
                try (ByteArrayInputStream bis = new ByteArrayInputStream(photoBytes)) {
                    blobClient.upload(bis, photoBytes.length, true);
                }
                blobClient.setHttpHeaders(headers);
                blobUrl = blobClient.getBlobUrl();
                logger.info("Uploaded photo {} to Azure Blob Storage: {}", file.getOriginalFilename(), blobUrl);
            } catch (Exception ex) {
                logger.error("Error uploading photo {} to Azure Blob Storage", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error uploading photo to storage. Please try again.");
                return result;
            }

            // Save metadata and blob URL to database
            Photo photo = new Photo(file.getOriginalFilename(), blobUrl, file.getSize(), file.getContentType());
            photo.setWidth(width);
            photo.setHeight(height);

            try {
                photo = photoRepository.save(photo);
                result.setSuccess(true);
                result.setPhotoId(photo.getId());
                logger.info("Successfully uploaded photo {} with ID {} — blob URL saved to database",
                    file.getOriginalFilename(), photo.getId());
            } catch (Exception ex) {
                logger.error("Error saving photo metadata to database for {}", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error saving photo to database. Please try again.");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during photo upload for {}", file.getOriginalFilename(), ex);
            result.setSuccess(false);
            result.setErrorMessage("An unexpected error occurred. Please try again.");
        }

        return result;
    }

    /**
     * Delete a photo — removes blob from Azure Blob Storage and metadata from the database
     */
    @Override
    public boolean deletePhoto(String id) {
        try {
            Optional<Photo> photoOpt = photoRepository.findById(id);
            if (photoOpt.isEmpty()) {
                logger.warn("Photo with ID {} not found for deletion", id);
                return false;
            }

            Photo photo = photoOpt.get();

            // Delete blob from Azure Blob Storage
            if (photo.getBlobUrl() != null && !photo.getBlobUrl().isBlank()) {
                try {
                    String blobName = extractBlobName(photo.getBlobUrl());
                    BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
                    blobClient.deleteIfExists();
                    logger.info("Deleted blob {} from Azure Blob Storage", blobName);
                } catch (Exception ex) {
                    logger.warn("Could not delete blob for photo ID {} — proceeding with database deletion", id, ex);
                }
            }

            // Delete metadata from database
            photoRepository.delete(photo);
            logger.info("Successfully deleted photo ID {} from database and Azure Blob Storage", id);
            return true;
        } catch (Exception ex) {
            logger.error("Error deleting photo with ID {} from database", id, ex);
            throw new RuntimeException("Error deleting photo", ex);
        }
    }

    /**
     * Get the previous photo (older) for navigation
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getPreviousPhoto(Photo currentPhoto) {
        List<Photo> olderPhotos = photoRepository.findPhotosUploadedBefore(currentPhoto.getUploadedAt());
        return olderPhotos.isEmpty() ? Optional.<Photo>empty() : Optional.of(olderPhotos.getFirst());
    }

    /**
     * Get the next photo (newer) for navigation
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getNextPhoto(Photo currentPhoto) {
        List<Photo> newerPhotos = photoRepository.findPhotosUploadedAfter(currentPhoto.getUploadedAt());
        return newerPhotos.isEmpty() ? Optional.<Photo>empty() : Optional.of(newerPhotos.getFirst());
    }

    /**
     * Extracts the blob name (path after container) from a full Azure Blob Storage URL
     */
    private String extractBlobName(String blobUrl) {
        // URL format: https://<account>.blob.core.windows.net/<container>/<blobName>
        int containerSlash = blobUrl.indexOf('/', blobUrl.indexOf(".blob.core.windows.net/") + ".blob.core.windows.net/".length());
        if (containerSlash >= 0) {
            return blobUrl.substring(containerSlash + 1);
        }
        // Fallback: everything after the last '/'
        return blobUrl.substring(blobUrl.lastIndexOf('/') + 1);
    }
}
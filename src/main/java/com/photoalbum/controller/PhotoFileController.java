package com.photoalbum.controller;

import com.photoalbum.model.Photo;
import com.photoalbum.service.PhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;
import java.util.Optional;

/**
 * Controller for serving photo files — redirects to Azure Blob Storage URL
 */
@Controller
@RequestMapping("/photo")
public class PhotoFileController {

    private static final Logger logger = LoggerFactory.getLogger(PhotoFileController.class);

    private final PhotoService photoService;

    public PhotoFileController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * Serves a photo by redirecting to its Azure Blob Storage URL
     */
    @GetMapping("/{id}")
    public ResponseEntity<Void> servePhoto(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warn("Photo file request with null or empty ID");
            return ResponseEntity.notFound().build();
        }

        try {
            Optional<Photo> photoOpt = photoService.getPhotoById(id);

            if (photoOpt.isEmpty()) {
                logger.warn("Photo with ID {} not found", id);
                return ResponseEntity.notFound().build();
            }

            Photo photo = photoOpt.get();
            String blobUrl = photo.getBlobUrl();

            if (blobUrl == null || blobUrl.isBlank()) {
                logger.error("No blob URL found for photo ID {}", id);
                return ResponseEntity.notFound().build();
            }

            logger.info("Redirecting photo ID {} ({}) to blob URL", id, photo.getOriginalFileName());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(blobUrl))
                    .build();
        } catch (Exception ex) {
            logger.error("Error serving photo with ID {}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
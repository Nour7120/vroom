package com.county_cars.vroom.modules.attachment.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachment endpoints — upload, download (stream), soft-delete.
 *
 * <p>All endpoints require a valid Bearer JWT.
 * Visibility rules are enforced inside the service layer, not here.</p>
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Hidden
@Tag(name = "Attachments", description = "Upload, download and delete file attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    // ─── POST /api/v1/attachments ─────────────────────────────────────────────────

    @Operation(
        summary = "Upload a file",
        description = """
            Uploads a file attachment and persists its metadata.

            **Validation applied:**
            - Extension whitelist (jpg, jpeg, png, gif, webp, pdf, doc, docx, xls, xlsx, mp4, mov, avi)
            - Max 10 MB for image categories (PROFILE_PHOTO, VEHICLE_IMAGE)
            - Max 25 MB for document categories
            - Magic-number (file signature) check for jpg, png, pdf, gif

            **Requires a valid Bearer JWT.**
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "File uploaded successfully",
            content = @Content(schema = @Schema(implementation = AttachmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed (size, type, signature)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @Parameter(description = "File to upload", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Visibility", required = true,
                schema = @Schema(implementation = AttachmentVisibility.class))
            @RequestParam("visibility") AttachmentVisibility visibility) {

        AttachmentResponse response = attachmentService.upload(file, visibility);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── GET /api/v1/attachments/{id} ────────────────────────────────────────────

    @Operation(
        summary = "Download a file",
        description = """
            Streams the file content.

            **Visibility rules enforced:**
            - `PUBLIC`     → any authenticated user
            - `PRIVATE`    → owner or ADMIN
            - `ADMIN_ONLY` → ADMIN role only

            **Requires a valid Bearer JWT.**
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File stream"),
        @ApiResponse(responseCode = "401", description = "Not authenticated or access denied",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Attachment not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = attachmentService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ─── DELETE /api/v1/attachments/{id} ─────────────────────────────────────────

    @Operation(
        summary = "Delete an attachment",
        description = """
            Soft-deletes the attachment record and removes the physical file from storage.

            **Only the owner or an ADMIN may delete.**

            **Requires a valid Bearer JWT.**
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated or not the owner",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Attachment not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attachmentService.delete(id);
    }
}

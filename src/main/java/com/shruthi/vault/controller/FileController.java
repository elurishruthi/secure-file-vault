package com.shruthi.vault.controller;

import com.shruthi.vault.model.FileRecord;
import com.shruthi.vault.model.User;
import com.shruthi.vault.repository.FileRecordRepository;
import com.shruthi.vault.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/file")
@CrossOrigin("*") // ✅ good for testing across tools
@RequiredArgsConstructor
public class FileController {

    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite
    ) throws IOException {
        System.out.println("📁 uploadFile() controller method was reached.");

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        String filename = file.getOriginalFilename();
        FileRecord existing = fileRecordRepository.findTopByFilenameAndOwner(filename, user);

        if (existing != null && !overwrite) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("File with the same name already exists. Use ?overwrite=true to overwrite it.");
        }

        if (existing != null && overwrite) {
            Files.deleteIfExists(Paths.get(existing.getStoragePath()));
            fileRecordRepository.delete(existing);
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        FileRecord record = FileRecord.builder()
                .filename(filename)
                .storagePath(filePath.toString())
                .uploadTime(LocalDateTime.now())
                .owner(user)
                .build();
        fileRecordRepository.save(record);

        return ResponseEntity.ok("File uploaded successfully: " + filename);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/list")
    public ResponseEntity<List<String>> listUserFiles() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<String> filenames = fileRecordRepository
                .findByOwner(user)
                .stream()
                .map(FileRecord::getFilename)
                .toList();

        return ResponseEntity.ok(filenames);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        FileRecord record = fileRecordRepository.findTopByFilenameAndOwner(filename, user);
        if (record == null) {
            throw new RuntimeException("File not found or access denied");
        }

        Path path = Paths.get(record.getStoragePath());
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Could not read the file");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
    
    
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/delete/{filename}")
    public ResponseEntity<String> deleteFile(@PathVariable String filename) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        FileRecord record = fileRecordRepository.findTopByFilenameAndOwner(filename, user);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or you do not have permission to delete it.");
        }

        // Delete from disk
        Files.deleteIfExists(Paths.get(record.getStoragePath()));

        // Delete from DB
        fileRecordRepository.delete(record);

        return ResponseEntity.ok("File deleted successfully: " + filename);
    }
    
    
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/admin/all-files")
    public ResponseEntity<List<String>> listAllFiles() {
        List<FileRecord> allRecords = fileRecordRepository.findAll();

        List<String> fileSummaries = allRecords.stream()
            .map(record -> String.format("User: %s | File: %s | Uploaded: %s",
                record.getOwner().getUsername(),
                record.getFilename(),
                record.getUploadTime()))
            .toList();

        return ResponseEntity.ok(fileSummaries);
    }


}

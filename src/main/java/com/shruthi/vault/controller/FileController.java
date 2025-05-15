package com.shruthi.vault.controller;

import com.shruthi.vault.model.FileRecord;
import com.shruthi.vault.model.Role;
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
@CrossOrigin("*")
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
        if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        String filename = file.getOriginalFilename();

        FileRecord existing = fileRecordRepository.findTopByFilenameAndOwnerAndDeletedFalse(filename, user);
        if (existing != null && !overwrite) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("File with same name exists. Use ?overwrite=true");
        }

        if (existing != null && overwrite) {
            Files.deleteIfExists(Paths.get(existing.getStoragePath()));
            fileRecordRepository.delete(existing);
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        FileRecord record = FileRecord.builder()
                .filename(filename)
                .storagePath(filePath.toString())
                .uploadTime(LocalDateTime.now())
                .owner(user)
                .deleted(false)
                .build();
        fileRecordRepository.save(record);

        return ResponseEntity.ok("File uploaded successfully: " + filename);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/list")
    public ResponseEntity<List<String>> listUserFiles() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<String> filenames = fileRecordRepository.findByOwnerAndDeletedFalse(user)
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

        FileRecord record = fileRecordRepository.findTopByFilenameAndOwnerAndDeletedFalse(filename, user);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Path path = Paths.get(record.getStoragePath());
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<List<String>> searchFiles(@RequestParam String keyword) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<FileRecord> results;
        if (user.getRole() == Role.ADMIN) {
            results = fileRecordRepository.findByFilenameContainingIgnoreCaseAndDeletedFalse(keyword);
        } else {
            results = fileRecordRepository.findByOwnerAndFilenameContainingIgnoreCaseAndDeletedFalse(user, keyword);
        }

        List<String> filenames = results.stream()
                .map(f -> String.format("User: %s | File: %s", f.getOwner().getUsername(), f.getFilename()))
                .toList();

        return ResponseEntity.ok(filenames);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/delete/{filename}")
    public ResponseEntity<String> deleteOwnFile(
            @PathVariable String filename,
            @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
            @RequestParam(name = "hard", defaultValue = "false") boolean hard
    ) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        FileRecord record = fileRecordRepository.findTopByFilenameAndOwnerAndDeletedFalse(filename, user);
        if (record == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");

        if (!confirm) return ResponseEntity.status(HttpStatus.CONFLICT).body("\u26a0\ufe0f Are you sure? Use ?confirm=true");

        if (hard) Files.deleteIfExists(Paths.get(record.getStoragePath()));
        record.setDeleted(true);
        fileRecordRepository.save(record);

        return ResponseEntity.ok("\u2705 File deleted: " + filename);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/delete-all")
    public ResponseEntity<String> deleteAllOwnFiles(
            @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
            @RequestParam(name = "hard", defaultValue = "false") boolean hard
    ) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<FileRecord> files = fileRecordRepository.findByOwnerAndDeletedFalse(user);

        if (!confirm) return ResponseEntity.status(HttpStatus.CONFLICT).body("\u26a0\ufe0f Are you sure? Use ?confirm=true");

        for (FileRecord record : files) {
            if (hard) Files.deleteIfExists(Paths.get(record.getStoragePath()));
            record.setDeleted(true);
            fileRecordRepository.save(record);
        }

        return ResponseEntity.ok("\u2705 All files deleted for user: " + username);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/admin/delete/{username}/{filename}")
    public ResponseEntity<String> deleteSpecificUserFile(
            @PathVariable String username,
            @PathVariable String filename,
            @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
            @RequestParam(name = "hard", defaultValue = "false") boolean hard
    ) throws IOException {
        User user = userRepository.findByUsername(username).orElseThrow();
        FileRecord record = fileRecordRepository.findTopByFilenameAndOwnerAndDeletedFalse(filename, user);
        if (record == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");

        if (!confirm) return ResponseEntity.status(HttpStatus.CONFLICT).body("\u26a0\ufe0f Are you sure? Use ?confirm=true");

        if (hard) Files.deleteIfExists(Paths.get(record.getStoragePath()));
        record.setDeleted(true);
        fileRecordRepository.save(record);

        return ResponseEntity.ok("\u2705 File deleted: " + filename + " for user: " + username);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/admin/delete-all/{username}")
    public ResponseEntity<String> deleteAllUserFiles(
            @PathVariable String username,
            @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
            @RequestParam(name = "hard", defaultValue = "false") boolean hard
    ) throws IOException {
        User user = userRepository.findByUsername(username).orElseThrow();
        List<FileRecord> files = fileRecordRepository.findByOwnerAndDeletedFalse(user);

        if (!confirm) return ResponseEntity.status(HttpStatus.CONFLICT).body("\u26a0\ufe0f Are you sure? Use ?confirm=true");

        for (FileRecord record : files) {
            if (hard) Files.deleteIfExists(Paths.get(record.getStoragePath()));
            record.setDeleted(true);
            fileRecordRepository.save(record);
        }

        return ResponseEntity.ok("\u2705 Deleted " + files.size() + " files for user: " + username);
    }
}

package com.shruthi.vault.repository;

import com.shruthi.vault.model.FileRecord;
import com.shruthi.vault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    List<FileRecord> findByOwner(User user);

    List<FileRecord> findByOwnerAndDeletedFalse(User user);

    FileRecord findTopByFilenameAndOwner(String filename, User owner);

    FileRecord findTopByFilenameAndOwnerAndDeletedFalse(String filename, User owner);

    List<FileRecord> findByFilenameContainingIgnoreCaseAndDeletedFalse(String keyword);

    List<FileRecord> findByOwnerAndFilenameContainingIgnoreCaseAndDeletedFalse(User user, String keyword);
}

package com.shruthi.vault.repository;

import com.shruthi.vault.model.FileRecord;
import com.shruthi.vault.model.User;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
	List<FileRecord> findByOwner(User user);
	//Optional<FileRecord> findByFilenameAndOwner(String filename, User owner);
	FileRecord findTopByFilenameAndOwner(String filename, User owner);


}

package com.shruthi.vault.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "file_records",
uniqueConstraints = {
        @UniqueConstraint(columnNames = {"filename", "owner_id"})
    })

public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    private String storagePath;

    private LocalDateTime uploadTime;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
    
    @Column(nullable = false)
    private boolean deleted = false;
}

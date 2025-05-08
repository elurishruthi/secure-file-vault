package com.shruthi.vault.dto;

import com.shruthi.vault.model.Role;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RegisterRequest {
	private String username;
    private String password;
    private Role role;  // Optional: can default to USER if not provided

}

package com.shruthi.vault.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class LoginRequest {
	private String username;
    private String password;

}

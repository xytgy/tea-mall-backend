package com.xytgy.teamallbackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class LoginRequest {
    @JsonAlias({"username", "useraccount"})
    private String userAccount;
    private String password;
}

package com.ticketapp.projetpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class WebAuthnDTOs {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RegistrationOptionsResponse {
        private String userHandle;
        private String challenge;
        private String rpName;
        private String rpId;
        private String userName;
        private String userDisplayName;
        private List<String> excludeCredentials;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegistrationFinishRequest {
        private String clientDataJson;
        private String attestationObject;
        private String challenge;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AuthenticationOptionsResponse {
        private String challenge;
        private List<String> allowCredentials;
        private String rpId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuthenticationFinishRequest {
        private String credentialId;
        private String clientDataJson;
        private String authenticatorData;
        private String signature;
        private String userHandle;
        private String challenge;
    }
}

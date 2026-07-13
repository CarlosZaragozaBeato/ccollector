package com.zensyra.collector.api.dto;

import jakarta.validation.constraints.NotBlank;

// No scope field, unlike the Strava register DTO: Suunto has no scope on the
// authorization redirect — the granted scope comes back in the token response.
public class SuuntoAthleteRegisterRequestDto {

    @NotBlank(message = "code is required")
    private String code;

    private String redirectUri;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}

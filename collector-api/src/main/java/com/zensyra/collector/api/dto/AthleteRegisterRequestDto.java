package com.zensyra.collector.api.dto;

import jakarta.validation.constraints.NotBlank;

public class AthleteRegisterRequestDto {

    @NotBlank(message = "code is required")
    private String code;

    private String redirectUri;

    // OAuth scope from the authorization redirect's `scope` query parameter.
    // Optional: Strava does not echo scope in the token-exchange response, so the
    // client forwards it here to record what the grant actually authorized.
    private String scope;

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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}

package com.pios.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** RFC 7591 client metadata (subset we honour; unknown fields ignored). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientRegistrationRequest {
    @JsonProperty("client_name")
    private String clientName;
    @JsonProperty("redirect_uris")
    private List<String> redirectUris;
    @JsonProperty("grant_types")
    private List<String> grantTypes;
    @JsonProperty("response_types")
    private List<String> responseTypes;
    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;
    private String scope;
}

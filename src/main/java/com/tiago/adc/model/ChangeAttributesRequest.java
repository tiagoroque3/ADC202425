package com.tiago.adc.model;

import java.util.Map;

public class ChangeAttributesRequest {
    public String token;
    public String targetUsername;
    public Map<String, String> newAttributes;
}

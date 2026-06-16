package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaGearDto {

    private String id;
    private String name;

    @JsonProperty("primary")
    private boolean primary;

    @JsonProperty("brand_name")
    private String brandName;

    @JsonProperty("model_name")
    private String modelName;

    private String description;
    private Float distance;
    private boolean retired;

    @JsonProperty("gear_type")
    private String gearType;

    public StravaGearDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Float getDistance() { return distance; }
    public void setDistance(Float distance) { this.distance = distance; }

    public boolean isRetired() { return retired; }
    public void setRetired(boolean retired) { this.retired = retired; }

    public String getGearType() { return gearType; }
    public void setGearType(String gearType) { this.gearType = gearType; }
}

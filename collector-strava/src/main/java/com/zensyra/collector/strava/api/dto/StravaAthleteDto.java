package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaAthleteDto {

    private Long id;
    private String username;
    private String firstname;
    private String lastname;
    private String city;
    private String country;
    private String sex;
    private Float weight;
    private String profile;

    @JsonProperty("measurement_preference")
    private String measurementPreference;

    private Integer ftp;

    @JsonProperty("follower_count")
    private Integer followerCount;

    @JsonProperty("friend_count")
    private Integer friendCount;

    private boolean premium;

    public StravaAthleteDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public Float getWeight() { return weight; }
    public void setWeight(Float weight) { this.weight = weight; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getMeasurementPreference() { return measurementPreference; }
    public void setMeasurementPreference(String measurementPreference) { this.measurementPreference = measurementPreference; }

    public Integer getFtp() { return ftp; }
    public void setFtp(Integer ftp) { this.ftp = ftp; }

    public Integer getFollowerCount() { return followerCount; }
    public void setFollowerCount(Integer followerCount) { this.followerCount = followerCount; }

    public Integer getFriendCount() { return friendCount; }
    public void setFriendCount(Integer friendCount) { this.friendCount = friendCount; }

    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
}

package com.zensyra.collector.strava.athlete;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "athletes")
public class Athlete extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strava_id", nullable = false, unique = true)
    private Long stravaId;

    @Column
    private String username;

    @Column
    private String firstname;

    @Column
    private String lastname;

    @Column
    private String city;

    @Column
    private String country;

    @Column(length = 1)
    private String sex;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(length = 500)
    private String profile;

    @Column(name = "measurement_preference", length = 20)
    private String measurementPreference;

    @Column
    private Integer ftp;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "friend_count")
    private Integer friendCount;

    @Column(nullable = false)
    private boolean premium = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStravaId() { return stravaId; }
    public void setStravaId(Long stravaId) { this.stravaId = stravaId; }

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

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

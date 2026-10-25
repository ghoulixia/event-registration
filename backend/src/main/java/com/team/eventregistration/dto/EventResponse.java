package com.team.eventregistration.dto;

import java.time.LocalDateTime;

public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private String organizerName;
    private Long organizerUserId;
    private String status;
    private int offerTtlMinutes;
    private LocalDateTime deletedAt;
    private String city;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer registeredCount;
    private Integer heldCount;
    private Integer remainingSlots;
    private String effectiveStatus;
    private Boolean canRegister;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public Long getOrganizerUserId() { return organizerUserId; }
    public void setOrganizerUserId(Long organizerUserId) { this.organizerUserId = organizerUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getOfferTtlMinutes() { return offerTtlMinutes; }
    public void setOfferTtlMinutes(int offerTtlMinutes) { this.offerTtlMinutes = offerTtlMinutes; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getRegisteredCount() { return registeredCount; }
    public Integer getHeldCount() { return heldCount; }
    public void setHeldCount(Integer heldCount) { this.heldCount = heldCount; }
    public void setRegisteredCount(Integer registeredCount) { this.registeredCount = registeredCount; }

    public Integer getRemainingSlots() { return remainingSlots; }
    public void setRemainingSlots(Integer remainingSlots) { this.remainingSlots = remainingSlots; }

    public String getEffectiveStatus() { return effectiveStatus; }
    public void setEffectiveStatus(String effectiveStatus) { this.effectiveStatus = effectiveStatus; }

    public Boolean getCanRegister() { return canRegister; }
    public void setCanRegister(Boolean canRegister) { this.canRegister = canRegister; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

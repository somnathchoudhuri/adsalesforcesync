// src/main/java/com/demo/adsync/model/ADUser.java
package com.demo.adsync.model;

import java.time.LocalDateTime;

public class ADUser {
    private String username;
    private String email;
    private String distinguishedName;
    private String department;
    private String manager;
    private String objectGuid;
    private String title;
    private LocalDateTime lastSynced;

    // Constructor
    public ADUser() {
        this.lastSynced = LocalDateTime.now();
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDistinguishedName() { return distinguishedName; }
    public void setDistinguishedName(String distinguishedName) { 
        this.distinguishedName = distinguishedName; 
    }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getManager() { return manager; }
    public void setManager(String manager) { this.manager = manager; }
    
    public String getObjectGuid() { return objectGuid; }
    public void setObjectGuid(String objectGuid) { this.objectGuid = objectGuid; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public LocalDateTime getLastSynced() { return lastSynced; }
    public void setLastSynced(LocalDateTime lastSynced) { 
        this.lastSynced = lastSynced; 
    }

    @Override
    public String toString() {
        return String.format("ADUser[username=%s, email=%s, department=%s]", 
            username, email, department);
    }
}

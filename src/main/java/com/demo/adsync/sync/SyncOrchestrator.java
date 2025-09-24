// src/main/java/com/demo/adsync/sync/SyncOrchestrator.java
package com.demo.adsync.sync;

import com.demo.adsync.model.ADUser;
import com.demo.adsync.service.LDAPService;
import com.demo.adsync.service.SalesforceService;
import com.unboundid.ldap.sdk.LDAPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SyncOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(SyncOrchestrator.class);
    
    private LDAPService ldapService;
    private SalesforceService salesforceService;

    public SyncOrchestrator(LDAPService ldapService, 
                           SalesforceService salesforceService) {
        this.ldapService = ldapService;
        this.salesforceService = salesforceService;
    }

    public void performSync() {
        logger.info("=== Starting AD to Salesforce Sync ===");
        
        try {
            // Connect to LDAP
            ldapService.connect();
            
            // Connect to Salesforce
            salesforceService.connect();
            
            // Fetch users from LDAP
            List<ADUser> users = ldapService.fetchUsers();
            logger.info("Fetched {} users from LDAP", users.size());
            
            // Sync to Salesforce
            if (!users.isEmpty()) {
                salesforceService.upsertUsers(users);
            } else {
                logger.warn("No users found in LDAP to sync");
            }
            
            logger.info("=== Sync completed successfully ===");
            
        } catch (LDAPException e) {
            logger.error("LDAP error during sync: {}", e.getMessage(), e);
            throw new RuntimeException("Sync failed due to LDAP error", e);
        } catch (Exception e) {
            logger.error("Unexpected error during sync: {}", e.getMessage(), e);
            throw new RuntimeException("Sync failed", e);
        } finally {
            // Clean up connections
            try {
                ldapService.disconnect();
            } catch (Exception e) {
                logger.warn("Error disconnecting from LDAP: {}", e.getMessage());
            }
            
            try {
                salesforceService.disconnect();
            } catch (Exception e) {
                logger.warn("Error disconnecting from Salesforce: {}", e.getMessage());
            }
        }
    }
}
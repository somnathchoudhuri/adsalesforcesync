// src/main/java/com/demo/adsync/service/LDAPService.java
package com.demo.adsync.service;

import com.demo.adsync.model.ADUser;
import com.unboundid.ldap.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LDAPService {
    private static final Logger logger = LoggerFactory.getLogger(LDAPService.class);
    
    private String ldapHost;
    private int ldapPort;
    private String bindDN;
    private String bindPassword;
    private String baseDN;
    private LDAPConnection connection;

    public LDAPService(String host, int port, String bindDN, 
                       String bindPassword, String baseDN) {
        this.ldapHost = host;
        this.ldapPort = port;
        this.bindDN = bindDN;
        this.bindPassword = bindPassword;
        this.baseDN = baseDN;
    }

    public void connect() throws LDAPException {
        logger.info("Connecting to LDAP server at {}:{}", ldapHost, ldapPort);
        connection = new LDAPConnection(ldapHost, ldapPort);
        
        // Bind to LDAP
        BindResult bindResult = connection.bind(bindDN, bindPassword);
        if (bindResult.getResultCode() == ResultCode.SUCCESS) {
            logger.info("Successfully connected to LDAP");
        } else {
            throw new LDAPException(bindResult.getResultCode(), 
                "Failed to bind to LDAP");
        }
    }

    public List<ADUser> fetchUsers() throws LDAPException {  // Fixed: Added throws declaration
        List<ADUser> users = new ArrayList<>();
        
        if (connection == null || !connection.isConnected()) {
            throw new LDAPException(ResultCode.CONNECT_ERROR, 
                "Not connected to LDAP");
        }

        // Search for users
        String searchFilter = "(objectClass=inetOrgPerson)";
        SearchRequest searchRequest = new SearchRequest(
            "ou=users," + baseDN,
            SearchScope.SUB,
            searchFilter,
            "cn", "uid", "mail", "departmentNumber", "title", 
            "entryUUID", "entryDN", "manager"
        );

        logger.info("Searching LDAP with filter: {}", searchFilter);
        
        try {
            SearchResult searchResult = connection.search(searchRequest);
            
            logger.info("Found {} LDAP entries", searchResult.getEntryCount());

            // Convert LDAP entries to ADUser objects
            for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                ADUser user = new ADUser();
                
                user.setUsername(entry.getAttributeValue("uid") != null ? 
                    entry.getAttributeValue("uid") : entry.getAttributeValue("cn"));
                user.setEmail(entry.getAttributeValue("mail"));
                user.setDistinguishedName(entry.getDN());
                user.setDepartment(entry.getAttributeValue("departmentNumber"));
                user.setTitle(entry.getAttributeValue("title"));
                user.setManager(entry.getAttributeValue("manager"));
                
                // Use entryUUID or cn as unique identifier
                String objectGuid = entry.getAttributeValue("entryUUID");
                if (objectGuid == null) {
                    objectGuid = entry.getAttributeValue("cn");
                }
                user.setObjectGuid(objectGuid);
                
                users.add(user);
                logger.debug("Processed user: {}", user);
            }
        } catch (LDAPSearchException e) {
            logger.error("LDAP search failed: {}", e.getMessage());
            throw new LDAPException(e.getResultCode(), 
                "Failed to search LDAP: " + e.getMessage(), e);
        }

        return users;
    }

    public void disconnect() {
        if (connection != null && connection.isConnected()) {
            connection.close();
            logger.info("Disconnected from LDAP");
        }
    }
}
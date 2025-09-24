// src/main/java/com/demo/adsync/service/SalesforceService.java
package com.demo.adsync.service;

import com.demo.adsync.model.ADUser;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.json.JSONArray;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class SalesforceService {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceService.class);
    
    private String username;
    private String password;
    private String securityToken;
    private String loginUrl;
    private String accessToken;
    private String instanceUrl;
    private static final String API_VERSION = "/services/data/v59.0";

    public SalesforceService(String username, String password, 
                            String securityToken, String loginUrl) {
        this.username = username;
        this.password = password;
        this.securityToken = securityToken;
        this.loginUrl = (loginUrl != null && !loginUrl.isEmpty()) ? 
            loginUrl : "https://login.salesforce.com";
    }

    public void connect() {
        logger.info("Connecting to Salesforce as {}", username);
        
        // SOAP body with partner API namespace
        String soapBody = String.format(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:urn=\"urn:partner.soap.sforce.com\">" +
            "<soapenv:Body>" +
            "<urn:login>" +
            "<urn:username>%s</urn:username>" +
            "<urn:password>%s%s</urn:password>" +
            "</urn:login>" +
            "</soapenv:Body>" +
            "</soapenv:Envelope>",
            username, password, securityToken
        );
        
        try {
            // Use partner WSDL endpoint
            String soapEndpoint = loginUrl;
            if (!soapEndpoint.contains("/services/")) {
                soapEndpoint = loginUrl + "/services/Soap/u/59.0";
            }
            
            logger.debug("Using SOAP endpoint: {}", soapEndpoint);
            
            HttpResponse<String> response = Unirest.post(soapEndpoint)
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", "login")
                .body(soapBody)
                .asString();
            
            logger.debug("SOAP Response Status: {}", response.getStatus());
            
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                
                // Extract sessionId and serverUrl from SOAP response
                accessToken = extractFromXml(responseBody, "<sessionId>", "</sessionId>");
                String serverUrl = extractFromXml(responseBody, "<serverUrl>", "</serverUrl>");
                
                if (accessToken == null || serverUrl == null) {
                    logger.error("Response body: {}", responseBody);
                    throw new RuntimeException("Failed to extract session info from response");
                }
                
                // Extract instance URL from server URL
                instanceUrl = serverUrl.substring(0, serverUrl.indexOf("/services/"));
                
                logger.info("Successfully connected to Salesforce at {}", instanceUrl);
                logger.debug("Session ID obtained: {}", accessToken.substring(0, 20) + "...");
            } else {
                logger.error("Login failed with status: {} - {}", 
                    response.getStatus(), response.getBody());
                throw new RuntimeException("Salesforce login failed");
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Salesforce", e);
            throw new RuntimeException("Connection failed: " + e.getMessage(), e);
        }
    }

    private String extractFromXml(String xml, String startTag, String endTag) {
        int start = xml.indexOf(startTag);
        if (start == -1) {
            logger.warn("Tag {} not found in XML", startTag);
            return null;
        }
        start += startTag.length();
        int end = xml.indexOf(endTag, start);
        if (end == -1) {
            logger.warn("End tag {} not found in XML", endTag);
            return null;
        }
        return xml.substring(start, end);
    }

    public void upsertUsers(List<ADUser> users) {
        if (users == null || users.isEmpty()) {
            logger.warn("No users to sync");
            return;
        }

        logger.info("Upserting {} users to Salesforce", users.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (ADUser user : users) {
            try {
                upsertUser(user);
                successCount++;
            } catch (Exception e) {
                failCount++;
                logger.error("Failed to upsert user: {}", user.getUsername(), e);
            }
        }
        
        logger.info("Upsert complete. Success: {}, Failed: {}", successCount, failCount);
    }

    private void upsertUser(ADUser user) throws Exception {
        // First, check if user exists
        String query = String.format(
            "SELECT Id FROM AD_User__c WHERE AD_Object_GUID__c = '%s' LIMIT 1",
            user.getObjectGuid().replace("'", "\\'")
        );
        
        HttpResponse<String> queryResponse = Unirest.get(instanceUrl + API_VERSION + "/query")
            .queryString("q", query)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .asString();
        
        if (queryResponse.getStatus() == 401) {
            // Try with different auth header format
            queryResponse = Unirest.get(instanceUrl + API_VERSION + "/query")
                .queryString("q", query)
                .header("Authorization", "OAuth " + accessToken)
                .header("Accept", "application/json")
                .asString();
        }
        
        if (queryResponse.getStatus() != 200) {
            // Check if the object exists
            if (queryResponse.getBody().contains("INVALID_TYPE") || 
                queryResponse.getBody().contains("sObject type 'AD_User__c' is not supported")) {
                logger.error("AD_User__c object does not exist in Salesforce. Please create it first.");
                logger.error("Instructions: Go to Setup → Object Manager → Create → Custom Object");
                throw new Exception("AD_User__c custom object not found in Salesforce");
            }
            throw new Exception("Query failed: " + queryResponse.getBody());
        }
        
        JSONObject queryResult = new JSONObject(queryResponse.getBody());
        
        // Prepare record data
        JSONObject record = new JSONObject();
        
        // IMPORTANT: Set the Name field (standard field) for better display
        // This will show the username instead of auto-number in list views
        String displayName = user.getUsername();
        if (displayName == null || displayName.isEmpty()) {
            displayName = user.getEmail();
            if (displayName == null || displayName.isEmpty()) {
                displayName = user.getObjectGuid();
            }
        }
        record.put("Name", displayName);
        
        // Set all custom fields
        record.put("AD_Username__c", user.getUsername() != null ? user.getUsername() : "");
        record.put("AD_Email__c", user.getEmail() != null ? user.getEmail() : "");
        record.put("AD_Distinguished_Name__c", 
            user.getDistinguishedName() != null ? user.getDistinguishedName() : "");
        record.put("AD_Department__c", 
            user.getDepartment() != null ? user.getDepartment() : "");
        
        // Handle manager field - truncate if too long
        if (user.getManager() != null) {
            String manager = user.getManager();
            if (manager.length() > 255) {
                manager = manager.substring(0, 255);
            }
            record.put("AD_Manager__c", manager);
        } else {
            record.put("AD_Manager__c", "");
        }
        
        record.put("AD_Object_GUID__c", user.getObjectGuid());
        
        // Add title if you have that field in Salesforce
        if (user.getTitle() != null && !user.getTitle().isEmpty()) {
            // Uncomment if you have AD_Title__c field in Salesforce
            // record.put("AD_Title__c", user.getTitle());
        }
        
        // Add formatted date
        if (user.getLastSynced() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            record.put("Last_Synced__c", user.getLastSynced().format(formatter) + "Z");
        }
        
        if (queryResult.getInt("totalSize") > 0) {
            // Update existing record
            JSONArray records = queryResult.getJSONArray("records");
            String recordId = records.getJSONObject(0).getString("Id");
            
            HttpResponse<String> updateResponse = Unirest.patch(
                instanceUrl + API_VERSION + "/sobjects/AD_User__c/" + recordId)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(record.toString())
                .asString();
            
            if (updateResponse.getStatus() == 204) {
                logger.info("Updated user: {} (ID: {})", displayName, recordId);
            } else {
                logger.error("Update failed for user {}: {}", displayName, updateResponse.getBody());
                throw new Exception("Update failed: " + updateResponse.getBody());
            }
        } else {
            // Create new record
            HttpResponse<String> createResponse = Unirest.post(
                instanceUrl + API_VERSION + "/sobjects/AD_User__c")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(record.toString())
                .asString();
            
            if (createResponse.getStatus() == 201) {
                JSONObject responseObj = new JSONObject(createResponse.getBody());
                String newId = responseObj.getString("id");
                logger.info("Created user: {} (ID: {})", displayName, newId);
            } else {
                logger.error("Create failed for user {}: {}", displayName, createResponse.getBody());
                throw new Exception("Create failed: " + createResponse.getBody());
            }
        }
    }

    public void disconnect() {
        // Clean up connections if needed
        try {
            // Unirest doesn't need explicit disconnect, but we can shut it down
            // Note: Only do this if you're completely done with all HTTP requests
            // Unirest.shutDown();  // Uncomment only if this is the final operation
        } catch (Exception e) {
            logger.warn("Error during disconnect: {}", e.getMessage());
        }
        logger.info("Salesforce service cleanup completed");
    }
    
    // Additional utility methods
    
    /**
     * Test the connection and return basic org info
     */
    public JSONObject getOrgInfo() {
        try {
            HttpResponse<String> response = Unirest.get(instanceUrl + API_VERSION)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .asString();
                
            if (response.getStatus() == 200) {
                return new JSONObject(response.getBody());
            } else {
                logger.error("Failed to get org info: {}", response.getBody());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error getting org info", e);
            return null;
        }
    }
    
    /**
     * Delete a user by GUID (useful for cleanup)
     */
    public boolean deleteUserByGuid(String guid) {
        try {
            // Find the record
            String query = String.format(
                "SELECT Id FROM AD_User__c WHERE AD_Object_GUID__c = '%s' LIMIT 1",
                guid.replace("'", "\\'")
            );
            
            HttpResponse<String> queryResponse = Unirest.get(instanceUrl + API_VERSION + "/query")
                .queryString("q", query)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .asString();
            
            if (queryResponse.getStatus() == 200) {
                JSONObject result = new JSONObject(queryResponse.getBody());
                if (result.getInt("totalSize") > 0) {
                    String recordId = result.getJSONArray("records")
                        .getJSONObject(0).getString("Id");
                    
                    // Delete the record
                    HttpResponse<String> deleteResponse = Unirest.delete(
                        instanceUrl + API_VERSION + "/sobjects/AD_User__c/" + recordId)
                        .header("Authorization", "Bearer " + accessToken)
                        .asString();
                    
                    if (deleteResponse.getStatus() == 204) {
                        logger.info("Deleted user with GUID: {}", guid);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error deleting user with GUID: {}", guid, e);
        }
        return false;
    }
    
    /**
     * Get all AD users from Salesforce
     */
    public JSONArray getAllADUsers() {
        try {
            String query = "SELECT Id, Name, AD_Username__c, AD_Email__c, " +
                          "AD_Department__c, AD_Object_GUID__c, Last_Synced__c " +
                          "FROM AD_User__c ORDER BY Name";
            
            HttpResponse<String> response = Unirest.get(instanceUrl + API_VERSION + "/query")
                .queryString("q", query)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .asString();
            
            if (response.getStatus() == 200) {
                JSONObject result = new JSONObject(response.getBody());
                return result.getJSONArray("records");
            }
        } catch (Exception e) {
            logger.error("Error fetching all AD users", e);
        }
        return new JSONArray();
    }
}
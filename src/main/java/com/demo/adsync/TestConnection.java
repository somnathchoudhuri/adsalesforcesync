package com.demo.adsync;

import com.demo.adsync.model.ADUser;
import com.demo.adsync.service.LDAPService;
import com.demo.adsync.service.SalesforceService;
import java.util.List;

public class TestConnection {
    public static void main(String[] args) {
        System.out.println("=== Testing Connections ===\n");
        
        // Test LDAP
        System.out.println("1. Testing LDAP Connection...");
        try {
            LDAPService ldap = new LDAPService(
                "localhost", 389, 
                "cn=admin,dc=demo,dc=local", 
                "ComplexPass123!", 
                "dc=demo,dc=local"
            );
            ldap.connect();
            System.out.println("   ✓ LDAP connection successful");
            
            // Try to fetch users
            List<ADUser> users = ldap.fetchUsers();
            System.out.println("   ✓ Found " + users.size() + " users in LDAP");
            
            if (!users.isEmpty()) {
                System.out.println("   Sample users:");
                users.stream().limit(3).forEach(u -> 
                    System.out.println("     - " + u.getUsername() + " (" + u.getEmail() + ")"));
            }
            
            ldap.disconnect();
        } catch (Exception e) {
            System.err.println("   ✗ LDAP connection failed: " + e.getMessage());
        }
        
        System.out.println("\n=== Test Complete ===");
    }
}
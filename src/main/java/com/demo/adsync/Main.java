// src/main/java/com/demo/adsync/Main.java
package com.demo.adsync;

import com.demo.adsync.service.LDAPService;
import com.demo.adsync.service.SalesforceService;
import com.demo.adsync.sync.SyncOrchestrator;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        try {
            // Load configuration
            Configurations configs = new Configurations();
            Configuration config = configs.properties("application.properties");
            
            // Create services
            LDAPService ldapService = new LDAPService(
                config.getString("ldap.host"),
                config.getInt("ldap.port"),
                config.getString("ldap.bindDN"),
                config.getString("ldap.bindPassword"),
                config.getString("ldap.baseDN")
            );
            
            SalesforceService salesforceService = new SalesforceService(
                config.getString("salesforce.username"),
                config.getString("salesforce.password"),
                config.getString("salesforce.securityToken"),
                config.getString("salesforce.loginUrl")
            );
            
            // Create orchestrator
            SyncOrchestrator orchestrator = new SyncOrchestrator(
                ldapService, salesforceService
            );
            
            // Run sync immediately
            orchestrator.performSync();
            
            // Schedule periodic sync if enabled
            if (config.getBoolean("sync.scheduled.enabled", false)) {
                scheduleSync(orchestrator, config);
            } else {
                logger.info("Scheduled sync is disabled. Exiting.");
            }
            
        } catch (Exception e) {
            logger.error("Application failed: ", e);
            System.exit(1);
        }
    }
    
    private static void scheduleSync(SyncOrchestrator orchestrator, 
                                    Configuration config) throws SchedulerException {
        String cronExpression = config.getString("sync.scheduled.cron", "0 0 * * * ?");
        
        JobDetail job = JobBuilder.newJob(SyncJob.class)
            .withIdentity("adSyncJob", "syncGroup")
            .build();
        
        // Pass orchestrator to job via JobDataMap
        job.getJobDataMap().put("orchestrator", orchestrator);
        
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("adSyncTrigger", "syncGroup")
            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
            .build();
        
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        
        logger.info("Scheduled sync with cron expression: {}", cronExpression);
    }
    
    // Inner class for Quartz job
    public static class SyncJob implements Job {
        private static final Logger logger = LoggerFactory.getLogger(SyncJob.class);
        
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            logger.info("Executing scheduled sync");
            
            SyncOrchestrator orchestrator = (SyncOrchestrator) 
                context.getJobDetail().getJobDataMap().get("orchestrator");
            
            try {
                orchestrator.performSync();
            } catch (Exception e) {
                logger.error("Scheduled sync failed: ", e);
                throw new JobExecutionException(e);
            }
        }
    }
}

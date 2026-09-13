package com.fairhome.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fairhome")
public class FairHomeProperties {

    private final DemoData demoData = new DemoData();
    private final Admin admin = new Admin();

    public DemoData getDemoData() {
        return demoData;
    }

    public Admin getAdmin() {
        return admin;
    }

    public static class DemoData {
        private boolean enabled = true;
        private int applications = 4000;
        private int duplicateReviewCount = 3;
        private int offlinePercent = 35;
        private String seed = "fairhome-demo";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getApplications() {
            return applications;
        }

        public void setApplications(int applications) {
            this.applications = applications;
        }

        public int getDuplicateReviewCount() {
            return duplicateReviewCount;
        }

        public void setDuplicateReviewCount(int duplicateReviewCount) {
            this.duplicateReviewCount = duplicateReviewCount;
        }

        public int getOfflinePercent() {
            return offlinePercent;
        }

        public void setOfflinePercent(int offlinePercent) {
            this.offlinePercent = offlinePercent;
        }

        public String getSeed() {
            return seed;
        }

        public void setSeed(String seed) {
            this.seed = seed;
        }
    }

    public static class Admin {
        private String officerName = "Allotment Officer";
        private String username = "admin";
        private String password = "FairHome@2026";

        public String getOfficerName() {
            return officerName;
        }

        public void setOfficerName(String officerName) {
            this.officerName = officerName;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}

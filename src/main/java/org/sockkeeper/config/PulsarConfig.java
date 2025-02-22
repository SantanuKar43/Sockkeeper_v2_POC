package org.sockkeeper.config;

public class PulsarConfig {
    private String serviceUrl;
    private String adminUrl;

    public String getServiceUrl() {
        return serviceUrl;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public void setAdminUrl(String adminUrl) {
        this.adminUrl = adminUrl;
    }
}

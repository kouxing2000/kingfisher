package com.kingfisher.proxy.config;


public class RuleConfig {

    private boolean disabled;

    private String url;

    private String script;

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public String toString() {
        return "RuleConfig{" +
                "disabled=" + disabled +
                ", url='" + url + '\'' +
                ", script='" + script + '\'' +
                '}';
    }
}
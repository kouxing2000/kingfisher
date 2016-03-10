package com.kingfisher.proxy.config;

/**
 * Created by weili5 on 2016/2/29.
 */
public class MappingConfig {
    private String to;

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MappingConfig{");
        sb.append("to='").append(to).append('\'');
        sb.append('}');
        return sb.toString();
    }
}

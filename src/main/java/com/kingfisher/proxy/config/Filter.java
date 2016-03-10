package com.kingfisher.proxy.config;

/**
 * Created by weili5 on 2016/2/29.
 */
public class Filter {

    private String type;
    private Object config;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Filter{");
        sb.append("type='").append(type).append('\'');
        sb.append(", config=").append(config);
        sb.append('}');
        return sb.toString();
    }
}

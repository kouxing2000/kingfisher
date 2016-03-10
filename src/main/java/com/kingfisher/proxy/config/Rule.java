package com.kingfisher.proxy.config;

import java.util.List;

public class Rule {

	private String url;
	private String regexUrl;
	private boolean disalbed;
	private List<Filter> filters;


	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getRegexUrl() {
		return regexUrl;
	}

	public void setRegexUrl(String regexUrl) {
		this.regexUrl = regexUrl;
	}

	public boolean isDisalbed() {
		return disalbed;
	}

	public void setDisalbed(boolean disalbed) {
		this.disalbed = disalbed;
	}

	public List<Filter> getFilters() {
		return filters;
	}

	public void setFilters(List<Filter> filters) {
		this.filters = filters;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("Rule{");
		sb.append("url='").append(url).append('\'');
		sb.append(", regexUrl='").append(regexUrl).append('\'');
		sb.append(", disalbed=").append(disalbed);
		sb.append(", filters=").append(filters);
		sb.append('}');
		return sb.toString();
	}
}
package com.stubhub.proxy.custom;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stubhub.proxy.Context;
import com.stubhub.proxy.resolver.InternetFileResolver;

/**
 * 
 * http://en.wikipedia.org/wiki/Pseudolocalization
 * 
 * @author weili5
 *
 */
public class PseudolocalizationHandler extends InternetFileResolver {
	
	private static final Logger logger = LoggerFactory.getLogger(PseudolocalizationHandler.class);

	//http://www.pseudolocalize.com/
	private static final char[] charsOld = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
	
	private static final char[] charsNew = "ÂßÇÐÉFGHÌJK£MNÓÞQR§TÛVWXÝZáβçδèƒϱλïJƙℓ₥ñôƥ9řƨƭúƲωж¥ƺ".toCharArray();
	
	private static final Pattern toBeTranslatedWord = Pattern.compile("\\\"[^\\\"]*?\\s+?[^\\\"]+?\\\"");
	
	@Override
	public HttpResponse read(String url, Context context) throws Exception {
		HttpResponse result = super.read(url, context);
		String contentType = result.headers().get(HttpHeaders.Names.CONTENT_TYPE);
		if (!contentType.contains("charset")) {
			result.headers().add(HttpHeaders.Names.CONTENT_TYPE, contentType + ";charset=UTF-8");
		}

		return result;
	}
	
	@Override
	protected byte[] generateResponseBytes(byte[] originalBytes, Context context) {
		// TODO charset from request? or response?
		try {
			String originalContent = new String(originalBytes, "UTF-8");
			
			String newContent = originalContent;
			
			Matcher matcher = toBeTranslatedWord.matcher(newContent);
			
			while(matcher.find()) {
				String group = matcher.group();
				String translate = translate(group);
				//logger.info("translate from {} to {}", group, translate);
				newContent = newContent.replace(group, translate);
			}
			
			return newContent.getBytes("UTF-8");
			
		} catch (Exception e) {
			logger.error("", e);
			return originalBytes;
		}
	}
	
	private static final String translate(String sentence) {
		int length = charsOld.length;
		
		for (int i = 0; i < length; i++) {
			char o = charsOld[i];
			char n = charsNew[i];
			sentence = sentence.replace(o, n);
		}
		
		return sentence;
	}
	
}

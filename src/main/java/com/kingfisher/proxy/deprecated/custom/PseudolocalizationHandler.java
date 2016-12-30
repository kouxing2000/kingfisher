package com.kingfisher.proxy.deprecated.custom;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingfisher.proxy.Context;
import com.kingfisher.proxy.resolver.InternetFileResolver;

/**
 * 
 * http://en.wikipedia.org/wiki/Pseudolocalization
 * 
 * @author weili5
 * 
 */
public class PseudolocalizationHandler extends InternetFileResolver implements CustomHandler {

	private static final Logger logger = LoggerFactory.getLogger(PseudolocalizationHandler.class);

	private static final char[] charsOld = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

	// from http://www.pseudolocalize.com/
	private static final char[] charsNew1 = "ÂßÇÐÉFGHÌJK£MNÓÞQR§TÛVWXÝZáβçδèƒϱλïJƙℓ₥ñôƥ9řƨƭúƲωж¥ƺ".toCharArray();
	// from pseudolocalize_char
	private static final char[] charsNew2 = "ÅβĈĐЄΓĞĦЇĵĶŁḾŃΘρqЯȘŦŨνẄ×¥ŽăτċďęƒĝћϊĵĸĺḿиόρQґšтΰVẅхỳź".toCharArray();
	// mine combined one - http://www.utf8-chartable.de/unicode-utf8-table.pl
	private static final char[] charsNew3 = "ÅβĈĐЀӺĞĤЇĵќĹḾŃÓᕉᕌȒŚŤŨᐺẄӼÝŽăᑳċďęƒĝȟϊĵƙĺḿñόƥɋґšťΰᏉẅӽỳź".toCharArray();

	// (\"[^\"]*?\s+?[^\"]+?\"|\"[A-Z][a-z]+?\")
	// http://jex.im/regulex/#!embed=false&flags=&re=(%5C%22%5B%5E%5C%22%5D*%3F%5Cs%2B%3F%5B%5E%5C%22%5D%2B%3F%5C%22%7C%5C%22%5BA-Z%5D%5Ba-z%5D%2B%3F%5C%22)
	private Pattern toBeTranslatedWord = Pattern
			.compile("(\\\"[^\\\"]*?\\s+?[^\\\"]+?\\\"|\\\"[A-Z][a-z]+?\\\")");

	public PseudolocalizationHandler() {
	}
	
	@Override
	public void initial(Map<String, String> parameters) {
		
		logger.info("initial with parameter={}", parameters);
		
		String regex = parameters.get("targetTextPatternInRegx");
		
		if (regex != null) {
			toBeTranslatedWord = Pattern.compile(regex);
		} else {
			logger.warn("using default pattern={}", toBeTranslatedWord);
		}
		
	}
	
	@Override
	public HttpResponse read(String url, Context context) throws Exception {
		HttpResponse result = super.read(url, context);
		String contentType = result.headers().get(HttpHeaders.Names.CONTENT_TYPE);
		if (!contentType.contains("charset")) {
			result.headers().remove(HttpHeaders.Names.CONTENT_TYPE);
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

			while (matcher.find()) {
				String group = matcher.group();
				String translate = translate(group);
				// logger.info("translate from {} to {}", group, translate);
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
			char n = charsNew3[i];
			sentence = sentence.replace(o, n);
		}

		return sentence;
	}

	/**
	 * https://android.googlesource.com/platform/build/+/donut-release/libs/host/pseudolocalize.cpp
	 * http://www.utf8-chartable.de/unicode-utf8-table.pl?start=0&number=1024&names=-&utf8=string-literal
	 * @param c
	 * @return
	 */
	private static final char pseudolocalize_char(char c) {
		switch (c) {
		case 'a':
			return 'ă';//"\xc4\x83";
		case 'b':
			return 'τ';//"\xcf\x84";
		case 'c':
			return 'ċ';//"\xc4\x8b";
		case 'd':
			return 'ď';//"\xc4\x8f";
		case 'e':
			return 'ę';//"\xc4\x99";
		case 'f':
			return 'ƒ';//"\xc6\x92";
		case 'g':
			return 'ĝ';//"\xc4\x9d";
		case 'h':
			return 'ћ';//"\xd1\x9b";
		case 'i':
			return 'ϊ';//"\xcf\x8a";
		case 'j':
			return 'ĵ';//"\xc4\xb5";
		case 'k':
			return 'ĸ';//"\xc4\xb8";
		case 'l':
			return 'ĺ';//"\xc4\xba";
		case 'm':
			return 'ḿ';//"\xe1\xb8\xbf";
		case 'n':
			return 'и';//"\xd0\xb8";
		case 'o':
			return 'ό';//"\xcf\x8c";
		case 'p':
			return 'ρ';//"\xcf\x81";
		case 'q':
			return 'Q';//"\x51"; ??
		case 'r':
			return 'ґ';//"\xd2\x91";
		case 's':
			return 'š';//"\xc5\xa1";
		case 't':
			return 'т';//"\xd1\x82";
		case 'u':
			return 'ΰ';//"\xce\xb0";
		case 'v':
			return 'V';//"\x56"; ??
		case 'w':
			return 'ẅ';//"\xe1\xba\x85";
		case 'x':
			return 'х';//"\xd1\x85";
		case 'y':
			return 'ỳ';//"\xe1\xbb\xb3";
		case 'z':
			return 'ź';//"\xc5\xba";
		case 'A':
			return 'Å';//"\xc3\x85";
		case 'B':
			return 'β';//"\xce\xb2";
		case 'C':
			return 'Ĉ';//"\xc4\x88";
		case 'D':
			return 'Đ';//"\xc4\x90";
		case 'E':
			return 'Є';//"\xd0\x84";
		case 'F':
			return 'Γ';//"\xce\x93";
		case 'G':
			return 'Ğ';//"\xc4\x9e";
		case 'H':
			return 'Ħ';//"\xc4\xa6";
		case 'I':
			return 'Ї';//"\xd0\x87";
		case 'J':
			return 'ĵ';//"\xc4\xb5";
		case 'K':
			return 'Ķ';//"\xc4\xb6";
		case 'L':
			return 'Ł';//"\xc5\x81";
		case 'M':
			return 'Ḿ';//"\xe1\xb8\xbe";
		case 'N':
			return 'Ń';//"\xc5\x83";
		case 'O':
			return 'Θ';//"\xce\x98";
		case 'P':
			return 'ρ';//"\xcf\x81";
		case 'Q':
			return 'q';//"\x71"; ??
		case 'R':
			return 'Я';//"\xd0\xaf";
		case 'S':
			return 'Ș';//"\xc8\x98";
		case 'T':
			return 'Ŧ';//"\xc5\xa6";
		case 'U':
			return 'Ũ';//"\xc5\xa8";
		case 'V':
			return 'ν';//"\xce\xbd";
		case 'W':
			return 'Ẅ';//"\xe1\xba\x84";
		case 'X':
			return '×';//"\xc3\x97";
		case 'Y':
			return '¥';//"\xc2\xa5";
		case 'Z':
			return 'Ž';//"\xc5\xbd";
		default:
			return c;//null;
		}
	}

	public static void main(String[] args) {
		int length = charsOld.length;

		for (int i = 0; i < length; i++) {
			char o = charsOld[i];
			System.out.print((o));
		}
		System.out.println();
		for (int i = 0; i < length; i++) {
			System.out.print(charsNew1[i]);
		}
		System.out.println();
		for (int i = 0; i < length; i++) {
			char o = charsOld[i];
			System.out.print(pseudolocalize_char(o));
		}
		System.out.println();
		for (int i = 0; i < length; i++) {
			System.out.print(charsNew3[i]);
		}
	}
}

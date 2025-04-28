package com.bharatpe.lending.lendingplatform.nbfc.util;

import com.bharatpe.lending.handlers.S3BucketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BuildersUtil {

	private final S3BucketHandler s3BucketHandler;

	public static String getGender(String gender) {
		if (Objects.nonNull(gender) && ("M".equalsIgnoreCase(gender) || "MALE".equalsIgnoreCase(gender))) {
			return "MALE";
		}
		if (Objects.nonNull(gender) && ("F".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender))) {
			return "FEMALE";
		}
		return "OTHERS";
	}

	public String getS3PresignedUrlFromKey(String key, String bucket) {
		log.info("key to fetch from aws: {}", key);
		return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
	}

	public static Optional<String> convertPreSignedUrlToBase64String(String url) {
		try {
			log.info("converting base64 from pre-signed url : {}", url);
			return Optional.of(Base64.encodeBase64String(
					IOUtils.toByteArray(URI.create(url).toURL().openConnection().getInputStream())));
		} catch (Exception exception) {
			log.error("error occurred while converting s3 object data to base64", exception);
			return Optional.empty();
		}
	}

	public static String convertXmlToBase64String(String xml) {
		try {
			if (!ObjectUtils.isEmpty(xml) && "\n".equalsIgnoreCase(xml.substring(xml.length() -1))) {
				System.out.println("removed last slash n");
				xml = xml.substring(0,xml.length() -1);
			}
			xml = xml.replaceAll("\\n","\n");
			return Base64.encodeBase64String(xml.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String convertXmlToString(String xml) {
		try {
			if (!ObjectUtils.isEmpty(xml) && "\n".equalsIgnoreCase(xml.substring(xml.length() -1))) {
				System.out.println("removed last slash n");
				xml = xml.substring(0,xml.length() -1);
			}
			xml = xml.replaceAll("\\n","\n");
			return xml;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public static Double getParsedFaceMatchPer(String faceMatchPer) {
		if (!ObjectUtils.isEmpty(faceMatchPer)) {
			try {
				return Double.parseDouble(faceMatchPer.replace("%", "").trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid faceMatchPer value: " + faceMatchPer, e);
			}
		}
		return null;
	}
}

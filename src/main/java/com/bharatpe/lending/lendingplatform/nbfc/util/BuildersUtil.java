package com.bharatpe.lending.lendingplatform.nbfc.util;

import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.EdiScheduleV2DTO;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.service.LendingEdiScheduleService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BuildersUtil {

    private final S3BucketHandler s3BucketHandler;
    private final LendingEdiScheduleService lendingEdiScheduleService;
    private final DateTimeUtil dateTimeUtil;

	@Value("${aws.s3.bucket:loan-document}")
	private String bucket;

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

	public Double getApr(Long merchantId, Long applicationId, Double amountToCalculateAprOn, Integer ediModel, String lender) {
		try {
			log.info("calculating APR for applicationId : {}", applicationId);
			Double guess = 0.01;
			ArrayList<Double> values = new ArrayList<>();
			CommonResponse response = lendingEdiScheduleService.getEdiScheduleV2(merchantId, applicationId, null);
			if (!response.isSuccess()) {
				log.info(response.getMessage());
				log.info("Unable to fetch edi schedule for APR calculation for applicationId : {}", applicationId);
				return null;
			}
			List<EdiScheduleV2DTO> ediSchedule = (List<EdiScheduleV2DTO>) response.getData();
			if (ObjectUtils.isEmpty(ediSchedule)) {
				log.info("Unable to fetch edi schedule for APR calculation for applicationid : {}", applicationId);
				return null;
			}
			values.add(0 - amountToCalculateAprOn);
			for (int i = 0; i < ediSchedule.size(); i++) {
				if (ediSchedule.get(i).getSerialNumber() == 0) continue;
				values.add(new Double(ediSchedule.get(i).getEdiAmount()));
				if ((i + 1) < ediSchedule.size()) {
					long diff = Math.abs(dateTimeUtil.getDateDiffInDays(ediSchedule.get(i).getDate(), ediSchedule.get(i + 1).getDate()));
					if (diff == 2) {
						values.add(0.0);
					}
				}
			}
			int tenureInDays = values.size() - 1;
			Double apr = 0.0;
			double[] valuesDouble = new double[values.size()];
			for (int i = 0; i < values.size(); i++) valuesDouble[i] = values.get(i);
			log.info("valuesDouble Size : {}", valuesDouble.length);
			int daysInYear = (ediModel == 7 && Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.CAPRI.name(), Lender.PAYU.name(), Lender.CREDITSAISON.name(), Lender.UGRO.name(), Lender.OXYZO.name()).contains(lender)) ? 360 : 365;
			apr = LoanCalculationUtil.irr(valuesDouble, guess) * daysInYear;
			if (apr.isNaN()) {
				log.info("APR : {}", apr);
				return null;
			}
			log.info("APR : {}", apr);
			return apr * 100;
		} catch (Exception e) {
			log.error("Unable to calculate APR for applicationId : {} Exception : {}, stacktrace : {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
		}
		return null;
	}
}

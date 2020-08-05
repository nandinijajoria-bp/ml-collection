package com.bharatpe.lending.handlers;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;

@Component
public class S3BucketHandler {
	private Logger logger = LoggerFactory.getLogger(S3BucketHandler.class);
	
	@Value("${aws.s3.credentials.accessKey}")
    private String accessKey;

    @Value("${aws.s3.credentials.secretKey}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;
    
	@Value("${aws.s3.loan.agreement.bucket}")
	private String bucket1;
	
	private AmazonS3 createS3BucketConnection() {
		AmazonS3 s3client = null;
		try {
			AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
			s3client = AmazonS3ClientBuilder.standard()
					  .withCredentials(new AWSStaticCredentialsProvider(credentials))
					  .withRegion(region)
					  .build();
		}catch(Exception e) {
			logger.info("Exception while creating connection to S3 bucket message : {}",e.getMessage());
		}
		return s3client;
	}
	
	public String uploadToS3Bucket(String base64Encoded, String fileName, String bucket) {
		Instant start = Instant.now();
		byte[] bI = org.apache.commons.codec.binary.Base64.decodeBase64(base64Encoded.getBytes());
		InputStream fis = new ByteArrayInputStream(bI);
		
		AmazonS3 s3client = createS3BucketConnection();
		try {
			if(s3client != null) {
				ObjectMetadata metadata = new ObjectMetadata();
				metadata.setContentLength(bI.length);
				metadata.setContentType("image/png");
				metadata.setCacheControl("public, max-age=31536000");
				
				s3client.putObject(bucket, fileName, fis, metadata);
			}
		}catch(Exception e) {
			logger.info("Exception while Uploading doc to S3 bucket message : {}",e.getMessage());
		}
		Instant end = Instant.now();
		logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
		return fileName;
	}
	
	public String getTemporaryPublicURL(String key, String bucket) throws FileNotFoundException {
	    try {
	    	logger.info("Getting temp URL for key: {} from bucket:{}", key, bucket);
			Instant start = Instant.now();
	    	AmazonS3 s3client = createS3BucketConnection();
			String tempUrl = s3client.generatePresignedUrl(bucket, key, new DateTime().plusMinutes(15).toDate()).toString();
			logger.info("Temp Url: {}", tempUrl);
			Instant end = Instant.now();
			logger.info("Time Taken by AWS S3 tempPublicURL API : {} miliseconds", Duration.between(start, end).toMillis());
			return tempUrl;
	    }
	    catch (AmazonS3Exception exception){
	        if(exception.getStatusCode() == 404){
	            throw new FileNotFoundException(key);
	        }
	        else{
	            throw exception;
	        }
	    }
	}

	public String getPreSignedPublicURL(String key, String bucket1)throws FileNotFoundException  {
		 try {
		    	logger.info("Getting temp URL for keu: {}", key);
				Instant start = Instant.now();
		    	AmazonS3 s3client = createS3BucketConnection();
				String tempUrl = s3client.generatePresignedUrl(bucket1, key, new DateTime().plusMinutes(7*24*60).toDate()).toString();
				logger.info("Temp Url: {}", tempUrl);
				Instant end = Instant.now();
				logger.info("Time Taken by AWS S3 tempPublicURL API : {} miliseconds", Duration.between(start, end).toMillis());
				return tempUrl;
		    }
		    catch (AmazonS3Exception exception){
		        if(exception.getStatusCode() == 404){
		            throw new FileNotFoundException(key);
		        }
		        else{
		            throw exception;
		        }
		    }
	}
}

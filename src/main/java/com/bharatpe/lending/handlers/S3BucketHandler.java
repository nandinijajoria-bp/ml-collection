package com.bharatpe.lending.handlers;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

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
import com.bharatpe.lending.constants.LendingConstants;

@Component
public class S3BucketHandler {
	private Logger logger = LoggerFactory.getLogger(S3BucketHandler.class);
	
	@Value("${aws.s3.bucket}")
	private String bucket;
	
	@Value("${aws.s3.credentials.accessKey}")
    private String accessKey;

    @Value("${aws.s3.credentials.secretKey}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;
	
	private AmazonS3 createS3BucketConnection() {
		AmazonS3 s3client = null;
		try {
			//create connection
			AWSCredentials credentials = new BasicAWSCredentials(
						accessKey, 
						secretKey
					);
			s3client = AmazonS3ClientBuilder
					  .standard()
					  .withCredentials(new AWSStaticCredentialsProvider(credentials))
					  .withRegion(region)
					  .build();
		}catch(Exception e) {
			e.printStackTrace();
			logger.info("Exception while creating connection to S3 bucket message : {}",e.getMessage());
		}
		return s3client;
	}
	
	public String uploadToS3Bucket(String base64Encoded, Long merchantId) {
		String fileName = "";
		//decode and convert into byte stream
		byte[] bI = org.apache.commons.codec.binary.Base64.decodeBase64(base64Encoded.getBytes());
		InputStream fis = new ByteArrayInputStream(bI);
		
		AmazonS3 s3client = createS3BucketConnection();
		try {
			if(s3client != null) {
				//set meta data
				ObjectMetadata metadata = new ObjectMetadata();
				metadata.setContentLength(bI.length);
				metadata.setContentType("image/png");
				metadata.setCacheControl("public, max-age=31536000");
				
				fileName = merchantId + "" + ((int)(Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
				
				//put object to s3 bucket
				s3client.putObject(
							bucket, 
							fileName,
							fis,
							metadata
						);
			}
		}catch(Exception e) {
			e.printStackTrace();
			logger.info("Exception while Uploading doc to S3 bucket message : {}",e.getMessage());
		}
		return fileName;
	}
	
	public String getTemporaryPublicURL(String key) throws FileNotFoundException {
	    try {
	    	AmazonS3 s3client = createS3BucketConnection();
	        return s3client.generatePresignedUrl(LendingConstants.AWS_S3_BUCKET_NAME, key, new DateTime().plusMinutes(15).toDate()).toString();
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
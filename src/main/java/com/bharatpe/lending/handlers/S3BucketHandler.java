package com.bharatpe.lending.handlers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.bharatpe.cache.service.LendingCache;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	
//	@Value("${aws.s3.credentials.accessKey}")
//    private String accessKey;
//
//    @Value("${aws.s3.credentials.secretKey}")
//    private String secretKey;

	@Value("${aws.s3.credentials}")
	String awsS3Credentials;

    @Value("${aws.s3.region}")
	String region;

	@Value("${aws.s3.role.arn}")
	String roleArn;

	@Value("${aws.s3.role.session.name}")
	String sessionName;
    
	@Value("${aws.s3.loan.agreement.bucket}")
	String bucket1;

	@Value("${aws.temp.cred.expiry.duration.minute}")
	Integer awsTempCredentialExpiryDurationInMinute;

	@Value("${aws.temp.cred.cache.duration.minute}")
	Integer awsTempCredentialCacheDurationInMinute;

	public static final String ACCESS_KEY_ID = "ACCESS_KEY_ID";
	public static final String SECRET_ACCESS_KEY = "SECRET_ACCESS_KEY";
	public static final String SESSION_TOKEN = "SESSION_TOKEN";
	public static final String AWS_STS_SESSION_CREDENTIAL_CACHE_KEY = "AWS_STS_SESSION_CREDENTIAL_LENDING";

	@Autowired
	LendingCache lendingCache;
	
	private AmazonS3 createS3BucketConnection() {
		AmazonS3 s3client = null;

		JSONObject jsonObject = new JSONObject(awsS3Credentials);

		String awsAccessKey  = (String) jsonObject.get("AccessKey");
		String awsSecretKey  = (String) jsonObject.get("SecretAccessKey");

		try {
//			AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
			AWSCredentials credentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey);
			s3client = AmazonS3ClientBuilder.standard()
					  .withCredentials(new AWSStaticCredentialsProvider(credentials))
					  .withRegion(region)
					  .build();
		}catch(Exception e) {
			logger.info("Exception while creating connection to S3 bucket message : {}",e.getMessage());
		}
		return s3client;
	}

	private AmazonS3 createS3WriteConnection() {
		try {
			// Creating the STS client is part of your trusted code. It has
			// the security credentials you use to obtain temporary security credentials.
			AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
			.withCredentials(new InstanceProfileCredentialsProvider(false))
			.withRegion(region)
			.build();

			Credentials sessionCredentials = null;
			String accessKeyId = null;
			String secretKey = null;
			String sessionToken = null;
			if(!lendingCache.isKeyExist(AWS_STS_SESSION_CREDENTIAL_CACHE_KEY)) {

				// Obtain credentials for the IAM role. Note that you cannot assume the role of an AWS root account;
				// Amazon S3 will deny access. You must use credentials for an IAM user or an IAM role.
				AssumeRoleRequest roleRequest = new AssumeRoleRequest()
				.withRoleArn(roleArn)
				.withRoleSessionName(sessionName)
				.withDurationSeconds(awsTempCredentialExpiryDurationInMinute * 60);

				AssumeRoleResult roleResponse = stsClient.assumeRole(roleRequest);
				sessionCredentials = roleResponse.getCredentials();
				Map<String, String> accessKeys = new HashMap();
				accessKeys.put(ACCESS_KEY_ID, sessionCredentials.getAccessKeyId());
				accessKeys.put(SECRET_ACCESS_KEY, sessionCredentials.getSecretAccessKey());
				accessKeys.put(SESSION_TOKEN, sessionCredentials.getSessionToken());
				lendingCache.updateHash(
					AWS_STS_SESSION_CREDENTIAL_CACHE_KEY,
					accessKeys,
					addMinutes(awsTempCredentialCacheDurationInMinute)
				);
				accessKeyId = sessionCredentials.getAccessKeyId();
				secretKey = sessionCredentials.getSecretAccessKey();
				sessionToken = sessionCredentials.getSessionToken();
				logger.info("[AWS_FRESH_TEMP_CREDENTIALS] Getting fresh set of temporary credentials");
			} else {
				Map<Object, Object> cachedSessionCreds = lendingCache.getHashEntries(AWS_STS_SESSION_CREDENTIAL_CACHE_KEY);
				accessKeyId = cachedSessionCreds.get(ACCESS_KEY_ID).toString();
				secretKey = cachedSessionCreds.get(SECRET_ACCESS_KEY).toString();
				sessionToken = cachedSessionCreds.get(SESSION_TOKEN).toString();
				logger.info("[AWS_CACHED_TEMP_CREDENTIALS] Getting cached set of temporary credentials");
			}

			logger.info("creds.sessionToken    [" + sessionToken + "]");
			logger.info("creds.accessKeyId     [" + accessKeyId + "]");
			logger.info("creds.secretAccessKey [" + secretKey + "]");

			// Create a BasicSessionCredentials object that contains the credentials you just retrieved.
			BasicSessionCredentials awsCredentials = new BasicSessionCredentials(accessKeyId, secretKey, sessionToken);


			// Provide temporary security credentials so that the Amazon S3 client
			// can send authenticated requests to Amazon S3. You create the client
			// using the sessionCredentials object.
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
			.withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
			.withRegion(region)
			.build();
			logger.info("[AWS_S3_CUSTOM_CONFIGURATION] Aws s3 custom configuration worked successfully");
			return s3Client;
		} catch(AmazonServiceException e) {
			// The call was transmitted successfully, but Amazon S3 couldn't process
			// it, so it returned an error response.
			e.printStackTrace();
		} catch(SdkClientException e) {
			// Amazon S3 couldn't be contacted for a response, or the client
			// couldn't parse the response from Amazon S3.
			e.printStackTrace();
		}
		return null;
	}

	public String uploadToS3Bucket(String base64Encoded, String fileName, String bucket) {
		Instant start = Instant.now();
		byte[] bI = org.apache.commons.codec.binary.Base64.decodeBase64(base64Encoded.getBytes());
		InputStream fis = new ByteArrayInputStream(bI);
		
		AmazonS3 s3client = createS3WriteConnection();
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
					logger.error("{}",exception);
		            throw new FileNotFoundException(key);
		        }
		        else{
					logger.error("{}",exception);
		            throw exception;
		        }
		    }
	}
	public String getPreSignedPublicURLWithExceptionHandled(String key, String bucket1) {
		try {
			return getPreSignedPublicURL(key,bucket1);
		} catch (Exception e) {
			logger.error("error occurred {}", e);
		}
		return "";
	}

	public String getS3Url(String key, String bucket1)  {
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
		catch (Exception exception){
			logger.info("Exception Occured while getting object from s3: {} {}", exception.getMessage(), exception);
		}
		return null;
	}

	public InputStream getObject(String key, String bucket) {
		try {
			logger.info("Fetching object for key:{}", key);
			Instant start = Instant.now();
			AmazonS3 s3client = createS3BucketConnection();
			InputStream inputStream = s3client.getObject(bucket, key).getObjectContent();
			Instant end = Instant.now();
			logger.info("Time Taken by AWS S3 getObject API : {} miliseconds", Duration.between(start, end).toMillis());
			return inputStream;
		}
		catch(Exception e) {
			logger.error("Exception while fetching object from s3", e);
		}
		return null;
	}

	public boolean doesS3ObjectExist(String bucketName, String objectKey) {

		// set up the S3 client
		AmazonS3 s3client = createS3BucketConnection();

		// check if the object exists
		try {
			ObjectMetadata metadata = s3client.getObjectMetadata(bucketName, objectKey);
			return true; // the object exists
		} catch (AmazonServiceException e) {
			if (e.getStatusCode() == 404) {
				logger.info("The object with key {} does not exist in the {} bucket.", objectKey, bucketName, e);
				return false; // the object does not exist
			} else {
				logger.info("An error occured while fetching objectmetadata for ObjectKey {} in bucket {}", objectKey, bucketName, e);
				return false; // an error occurred, assume the object does not exist
			}
		} catch (SdkClientException e) {
			logger.info("An error occured while fetching objectmetadata for ObjectKey {} in bucket {}", objectKey, bucketName, e);
			return false; // an error occurred, assume the object does not exist
		}
	}

	public void uploadFileToS3(File file, String bucket, String fileName) {
		Instant start = Instant.now();
		AmazonS3 s3client = createS3WriteConnection();
		try {
			if(s3client != null) {
				s3client.putObject(bucket, fileName, file);
			}
		}catch(Exception e) {
			logger.info("Exception while uploading file to S3", e);
		}
		Instant end = Instant.now();
		logger.info("Time Taken by AWS S3 File upload API : {} miliseconds", Duration.between(start, end).toMillis());
	}

	public void uploadFileToS3WithTtl(InputStream inputStream, String bucket, String fileName, int ttlInDays) {
		Instant start = Instant.now();
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setExpirationTime(DateTime.now().plusDays(ttlInDays).toDate());
		AmazonS3 s3client = createS3WriteConnection();

		try {
			if(s3client != null) {
				s3client.putObject(bucket, fileName, inputStream, metadata);
			}
		}catch(Exception e) {
			logger.info("Exception while uploading file to S3", e);
		}
		Instant end = Instant.now();
		logger.info("Time Taken by AWS S3 File upload API : {} miliseconds", Duration.between(start, end).toMillis());
	}

	public String uploadToS3PdfBucket(InputStream pdfStream, String fileName, String bucket) {
		Instant start = Instant.now();
		AmazonS3 s3client = createS3WriteConnection();
		try {
			if(s3client != null) {
				ObjectMetadata metadata = new ObjectMetadata();
				metadata.setContentType("application/pdf");
				metadata.setCacheControl("public, max-age=31536000");

				s3client.putObject(bucket, fileName, pdfStream, metadata);
			}
		}catch(Exception e) {
			logger.info("Exception while Uploading doc to S3 bucket message : {}",e.getMessage());
		}
		Instant end = Instant.now();
		logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
		return fileName;
	}

	public static Date addMinutes(Integer minutes) {
		Date date = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		c.add(Calendar.MINUTE, minutes);
		return c.getTime();
	}
}

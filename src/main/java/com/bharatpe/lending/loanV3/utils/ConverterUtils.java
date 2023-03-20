package com.bharatpe.lending.loanV3.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ConverterUtils {
    public static String convertPreSignedUrlToBase64String(String selfiePresignedUrl) {
        try {
            return Base64.encodeBase64String(IOUtils.toByteArray(URI.create(selfiePresignedUrl).toURL().openConnection().getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
            log.error("error occurred while converting data {}", e.getMessage());
        }
        return "";
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

    public static String convertToBase64String(String data) {
        try {
            return Base64.encodeBase64String(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
    public String parseData(String data) {
        return data.replaceAll("#", " ").replaceAll("‘", " ")
                .replaceAll("\""," ").replaceAll("_", " ")
                .replaceAll("-", " ").replaceAll("~"," ")
                .replaceAll("/", " ").replaceAll("!", " ")
                .replaceAll("\\*"," ").replaceAll("\\(", " ")
                .replaceAll("\\)", " ").replaceAll("%", " ").replaceAll("`"," ");
    }
    public String parseDataExtended(String data) {
        return data.replaceAll("#", " ").replaceAll("‘", " ")
                .replaceAll("\""," ").replaceAll("_", " ")
                .replaceAll("-", " ").replaceAll("~"," ")
                .replaceAll("/", " ").replaceAll("!", " ")
                .replaceAll("\\*"," ").replaceAll("\\(", " ")
                .replaceAll("\\)", " ").replaceAll("%", " ").replaceAll("`"," ")
                .replaceAll("&"," ");
    }

    public String parseNameData(String data) {
        return data.replaceAll("#", "").replaceAll("‘", "")
                .replaceAll("\"","").replaceAll("_", "")
                .replaceAll("-", "").replaceAll("~","")
                .replaceAll("/", "").replaceAll("!", "")
                .replaceAll("\\*","").replaceAll("\\(", "")
                .replaceAll("\\)", "").replaceAll("%", "")
                .replaceAll("`","").replaceAll("\\.", "").replaceAll(",", "");
    }

}

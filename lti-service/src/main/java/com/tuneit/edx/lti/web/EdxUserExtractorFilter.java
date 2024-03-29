package com.tuneit.edx.lti.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneit.edx.lti.config.WebConfig;
import com.tuneit.edx.lti.to.EdxUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;

/**
 * edx-user-info extractor filter
 * @author alex
 */
@Component
@Slf4j
public class EdxUserExtractorFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        try {
            String cookies = request.getHeader("cookie");
            String userInfoCookie = Arrays
                    .stream(cookies.split("; "))
                    .map(String::trim)
                    .filter(s -> s.startsWith("edx-user-info"))
                    .findFirst().orElse(null);

            if (userInfoCookie != null) {
                try {
                    String value = userInfoCookie.split("=")[1];
                    value = value
                            .trim()
                            .substring(1, value.length() - 1)
                            .replaceAll("\\\\054", ",")
                            .replaceAll("\\\\", "");

                    log.info("VALUE FOR PARSING {}", value);
                    ObjectMapper om = new ObjectMapper();
                    om.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                    EdxUserInfo userInfo = om.readValue(value, EdxUserInfo.class);
                    log.info("Detect edx-user-info");
                    servletRequest.setAttribute(WebConfig.ATTRIBUTE_USER_INFO, userInfo);
                } catch (JsonMappingException jme) {
                    jme.printStackTrace();
                }
            }
        } catch (NullPointerException npe) {
            log.info("USER NOT FOUND");
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}

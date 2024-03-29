package com.tuneit.edx.lti.rest.in;

import com.tuneit.edx.lti.to.TasksForm;
import com.tuneit.edx.lti.unit.ModelViewProcessor;
import lombok.extern.slf4j.Slf4j;
import org.imsglobal.aspect.Lti;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Controller for handling queries from EDX platform by LTI protocol
 */
@Slf4j
@Controller
@Profile("prod")
public class EdxLtiController implements LtiHandler {

    @Autowired
    private ModelViewProcessor modelViewProcessor;

    /**
     * @param labId      identifier of current lab
     * @param sourcedId  LIS identifier
     * @param serviceUrl LIS service url for sending score
     * @param request    HTTP-request object
     * @param model      contains objects for rendering thymeleaf pages
     * @return path to thymeleaf template with main page
     */
    @Lti
    @Override
    @PostMapping(MAIN_QUERY_URL)
    public String handleMainQuery(
            @RequestParam(name = LIS_LAB_ID_NAME) String labId,
            @RequestParam(name = LIS_SOURCED_ID_NAME) String sourcedId,
            @RequestParam(name = LIS_OUTCOME_URL_NAME) String serviceUrl,
            HttpServletRequest request, Map<String, Object> model,
            @PathVariable int taskId
    ) {

        // pre handling here
        // redirect call to logical unit
        return modelViewProcessor.renderMain(labId, sourcedId, serviceUrl, request, model, taskId);
    }

    @Lti
    @PostMapping(RESULT_QUERY_URL)
    public String handleResultsQuery(
            @RequestParam(name = LIS_LAB_ID_NAME) String labId,
            @RequestParam(name = USERNAME_NAME) String username,
            HttpServletRequest request,
            Map<String, Object> model,
            @ModelAttribute TasksForm queryForm,
            @PathVariable int taskId
    ) {

        // you can add pre handling here

        // redirect call to logical unit
        return modelViewProcessor.renderResult(labId, request, username, model, queryForm, taskId);
    }
}

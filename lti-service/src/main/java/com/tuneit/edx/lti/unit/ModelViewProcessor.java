package com.tuneit.edx.lti.unit;

import com.tuneit.courses.db.generate.*;
import com.tuneit.courses.db.service.TaskGeneratorService;
import com.tuneit.edx.lti.to.TasksForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.tuneit.edx.lti.rest.out.ScoreSender;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Profile({"dev", "prod"})
public class ModelViewProcessor {

    private static final String PATH_TO_MAIN_PAGE = "index";
    private static final String PATH_TO_RESULTS_PAGE = "result";
    private static final Map<String, Map<Integer, LTIKey>> TASK_MAP = new ConcurrentHashMap<>();
    // TODO see ticket #3
    private static int variant = 0;
    @Autowired
    private TaskGeneratorService service;
    @Autowired
    private ScoreSender scoreSender;
    @Autowired
    private Labs labs;
    @Autowired
    private SchemaLoader schemaLoader;

    /**
     * Render main page of the service's web application
     * @param labId Lab work ID
     * @param sourcedId sourceId parameter
     * @param serviceUrl LTI service URL
     * @param request HTTP request to process
     * @param model Map for store context
     * @param taskId ID of the task
     * @return Path to main page
     */
    public String renderMain(String labId, String sourcedId, String serviceUrl,
                             HttpServletRequest request, Map<String, Object> model, int taskId) {
        String[] splittedSourceId = sourcedId.split(":");
        String username = splittedSourceId[splittedSourceId.length - 1];

        // Put parameters required for successful page rendering into map
        model.put("numberOfLab", labId);
        model.put("userID", username);

        // TODO temporary hardcode. See ticket #3 and #2
        // TODO FIX variant increment
        Task task = service.getTask(username, Integer.valueOf(labId.substring(3)) - 1, taskId, String.valueOf(/*variant++*/variant), 0); //FIXME
        model.put("task", task.getQuestion());
        model.put("taskId", taskId);

        synchronized (TASK_MAP) {
            if (!TASK_MAP.containsKey(username)) {
                TASK_MAP.put(username, new ConcurrentHashMap<>());
            }
        }
        Map<Integer, LTIKey> LTIKeys = TASK_MAP.get(username);
        LTIKeys.put(taskId, new LTIKey(sourcedId, serviceUrl));
        TASK_MAP.put(username, LTIKeys);

        TasksForm queryForm = new TasksForm();
        model.put("query", queryForm);

        return PATH_TO_MAIN_PAGE;
    }

    /**
     * Render page with query check results
     * @param labId Lab work ID
     * @param request HTTP request to process
     * @param username Name of the course participant user
     * @param model Map for store context
     * @param queryForm Query form reference
     * @param taskId ID of the task
     * @return Path to main page
     */
    public String renderResult(String labId, HttpServletRequest request, String username,
                               Map<String, Object> model, TasksForm queryForm, int taskId) {
        
        // Put parameters required for successful page rendering into map
        model.put("numberOfLab", labId);
        Task task = service.getTask(username, Integer.valueOf(labId.substring(3)) - 1, taskId, String.valueOf(/*variant++*/variant), 0);//FIXME
        task.setAnswer(queryForm.getTextQuery());

        task.setComplete(!(task.getAnswer() == null || task.getAnswer().isEmpty()));

        service.checkTasks(task);

        int resultId = (new Date().toString() + username).hashCode();
        resultId = Math.abs(resultId);

        log.info("{} result for task{}\n{}", username, taskId, task);
        log.info("ID: {}. Question: {}. Student Answer: {}. System answer: {}",
                resultId, task.getQuestion(), task.getAnswer(), getCorrectAnswer(task));

        model.put("queryText", getSQLStringWithComments(task));
        model.put("id", resultId);
        model.put("rating", String.format("%.2f", task.getRating() * 100) + "%");

        try {
            LTIKey ltiKey = TASK_MAP.get(username).get(taskId);
            log.info("Push score to URL: " + ltiKey.getOutcomeUrl());
            scoreSender.push(ltiKey.getSourcedId(), ltiKey.getOutcomeUrl(), task.getRating());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return PATH_TO_RESULTS_PAGE;
    }

    /**
     * Get correct answer query
     * @param task Task
     * @return Correct answer string
     */
    private String getCorrectAnswer(Task task) {
        try {
            Lab lab = labs.getLab(task.getLabId());
            LabTaskQA labTaskQA = lab.generate(task, schemaLoader.getSchema(task.getLabId()));

            return labTaskQA.getCorrectAnswer();
        } catch (Exception e) {
            return "Failed to get a answer";
        }
    }

    /**
     * Get debug output string for task
     * @param task Task
     * @return Formatted debug output string
     */
    private String getSQLStringWithComments(Task task) {
        return "# \n# " +
                task.getQuestion() +
                "\n# \n# " +
                ((task.getRating() < 0.1f) ? "Ответ неверный" : "Ответ верный") +
                "\n# \n" +
                task.getAnswer();
    }

    @Data
    @AllArgsConstructor
    @ToString
    private static class LTIKey {
        private String sourcedId;
        private String outcomeUrl;
    }
}

package com.tuneit.edx.lti.rest;

import com.tuneit.edx.lti.config.WebConfig;
import com.tuneit.edx.lti.cources.Service;
import com.tuneit.edx.lti.cources.ServiceFactory;
import com.tuneit.edx.lti.cources.Task;
import com.tuneit.edx.lti.to.EdxUserInfo;
import com.tuneit.edx.lti.to.TasksForm;
import org.imsglobal.aspect.Lti;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Random;

@Controller
public class LtiHandler {

    public static final String LIS_SOURCED_ID_NAME  = "lis_result_sourcedid";

    public static final String LIS_OUTCOME_URL_NAME = "lis_outcome_service_url";

    /**
     * Метод для отладки (для dev режима). Используется
     * движком themyleaf при GET запросах (когда запросы
     * ходят напрямую из браузеров).
     *
     * @param model       объекты, которые нудны будут для рендеринга html-странички (themyleaf)
     * @return название themyleaf-шаблона, который будет обработан, как результат текущего запроса
     */
    @GetMapping("/debug/edx/home")
    public String doGet(@RequestParam(name=LIS_SOURCED_ID_NAME, required = false) String sourcedId,
                        @RequestParam(name=LIS_OUTCOME_URL_NAME, required = false) String serviceUrl,
                        HttpSession session,
                        HttpServletRequest request,
                        Map<String, Object> model) {

        // TODO needs move to debug filter
        EdxUserInfo userInfo = (EdxUserInfo) request.getAttribute(WebConfig.ATTRIBUTE_USER_INFO);
        if(userInfo == null) {
            EdxUserInfo tmpUser = new EdxUserInfo();
            tmpUser.setUsername("DEBUG_USER");
            tmpUser.setVersion(1);
            tmpUser.setHeaderUrls(null);
            request.setAttribute(WebConfig.ATTRIBUTE_USER_INFO, tmpUser);
        }

        // вся обработка в режиме отладки эквивалентна продакшн режиму
        return doPost (
                "1",
                sourcedId == null ? "DEBUG_ID" : sourcedId,
                serviceUrl == null ? "DEBUG_URL" : serviceUrl,
                session,
                request,
                model
        );
    }

    /**
     * Обработчик запросов к LTI-сервису
     *
     * @param labId       идендификатор задания
     * @param model       объекты, которые нудны будут для рендеринга html-странички (themyleaf)
     * @return
     */
    @Lti
    @PostMapping("/api/rest/lti/{labId}")
    public String doPost(@PathVariable("labId") String labId,
                         @RequestParam(name=LIS_SOURCED_ID_NAME) String sourcedId,
                         @RequestParam(name=LIS_OUTCOME_URL_NAME) String serviceUrl,
                         HttpSession session,
                         HttpServletRequest request,
                         Map<String, Object> model) {

        EdxUserInfo userInfo = (EdxUserInfo) request.getAttribute(WebConfig.ATTRIBUTE_USER_INFO);

        /**  вставляем параметры, необходимые для рендера страницы в мапу  */
        model.put("numberOfLab", labId );

        Service dataStore = ServiceFactory.getExampleService(); // TODO change to getDataStoreService()
        Task[] tasks = dataStore.getTasks(userInfo.getUsername(), labId, session.getId(), 5);// TODO 5 - temporary hardcode
        model.put("task1", tasks[0].getQuestion());
        model.put("task2", tasks[1].getQuestion());
        model.put("task3", tasks[2].getQuestion());
        model.put("task4", tasks[3].getQuestion());
        model.put("task5", tasks[4].getQuestion());

        session.setAttribute("tasks", tasks);


        checkLisParams(sourcedId, serviceUrl, session);

        TasksForm queryForm = new TasksForm();
        model.put("query", queryForm);

        /**  передаем управление движку themyleaf (см. classpath:/templates/index.html)  */
        return "index";
    }

    @Lti
    @PostMapping("/api/rest/lti/{labId}/result")
    public String doResult( @PathVariable("labId") String labId,
                            HttpServletRequest request,
                            Map<String, Object> model,
                            @ModelAttribute TasksForm queryForm) {

        EdxUserInfo userInfo = (EdxUserInfo) request.getAttribute(WebConfig.ATTRIBUTE_USER_INFO);

        String username = userInfo == null ? "Unknown" :
                ( userInfo.getUsername() == null ? "Guest" : userInfo.getUsername() );

        // TODO validation answers here

        /**  вставляем параметры, необходимые для рендера страницы в мапу  */
        model.put("username", username);
        model.put("numberOfLab", labId );
        model.put("queryText", queryForm.getTextQuery());
        model.put("queryText2", queryForm.getTextQuery2());
        model.put("queryText3", queryForm.getTextQuery3());
        model.put("queryText4", queryForm.getTextQuery4());
        model.put("queryText5", queryForm.getTextQuery5());

        Task[] tasks = (Task[]) request.getSession().getAttribute("tasks");
        tasks[0].setAnswer(queryForm.getTextQuery());
        tasks[1].setAnswer(queryForm.getTextQuery2());
        tasks[2].setAnswer(queryForm.getTextQuery3());
        tasks[3].setAnswer(queryForm.getTextQuery4());
        tasks[4].setAnswer(queryForm.getTextQuery5());
        for(Task t : tasks) {
            t.setComplete( !(t.getAnswer() == null || t.getAnswer().isEmpty()) );
        }

        Service dataStore = ServiceFactory.getExampleService(); // TODO change to getDataStoreService()
        dataStore.checkTasks(tasks);

        float x = 0;
        for(Task t : tasks) {
            x += t.getRating();
        }

        model.put("rating", String.format("%.2f", x * 100 / 5) + "%");

        try {
            String serviceUrl = (String) request.getSession().getAttribute(LIS_OUTCOME_URL_NAME);
            String sourcedId = (String) request.getSession().getAttribute(LIS_SOURCED_ID_NAME);

            System.out.println("Push score to URL: " + serviceUrl);

            // TODO needs add parameter `score`
            int result = ScoreSender.push(sourcedId, serviceUrl);
            System.out.println("RETURN CODE = " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "result";
    }

    public void checkLisParams(String sourcedId, String outcomeUrl, HttpSession session) {
        String sessionSourcedId = (String) session.getAttribute(LIS_SOURCED_ID_NAME);
        String sessionOutcomeUrl = (String) session.getAttribute(LIS_OUTCOME_URL_NAME);
        if(sourcedId != null && !sourcedId.equals(sessionSourcedId)) {
            session.setAttribute(LIS_SOURCED_ID_NAME, sourcedId);
        }
        if(outcomeUrl != null && !outcomeUrl.equals(sessionOutcomeUrl)) {
            session.setAttribute(LIS_OUTCOME_URL_NAME, outcomeUrl);
        }
    }

    private static Random rnd = new Random();
}

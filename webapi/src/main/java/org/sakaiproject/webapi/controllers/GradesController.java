/******************************************************************************
 * Copyright 2015 sakaiproject.org Licensed under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.sakaiproject.webapi.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.grading.api.CourseGradeTransferBean;
import org.sakaiproject.grading.api.GradeDefinition;
import org.sakaiproject.grading.api.GradingConstants;
import org.sakaiproject.portal.api.PortalService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.webapi.beans.CourseGradeRestBean;
import org.sakaiproject.webapi.beans.GradeRestBean;
import org.sakaiproject.webapi.beans.SimpleSiteRestBean;
import org.sakaiproject.webapi.beans.SiteLatestGradesBean;
import org.sakaiproject.webapi.beans.UserGradesRestBean;
import org.sakaiproject.webapi.beans.UserLatestGradeBean;
import org.sakaiproject.webapi.beans.UserRestBean;
import org.sakaiproject.webapi.beans.AssesmentGradeRestBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;


/**
 */
@Slf4j
@RestController
public class GradesController extends AbstractSakaiApiController {

    @Resource
    private EntityManager entityManager;

    @Resource(name = "org.sakaiproject.grading.api.GradingService")
    private org.sakaiproject.grading.api.GradingService gradingService;

    @Resource
    private PortalService portalService;

    @Resource
    private SiteService siteService;

    @Resource
    private SqlService sqlService;

    @Resource
    private AssignmentService assignmentService;

    private final Function<String, List<GradeRestBean>> gradeDataSupplierForSite = (siteId) -> {

        List<org.sakaiproject.grading.api.Assignment> assignments = gradingService.getViewableAssignmentsForCurrentUser(siteId);
        List<Long> assignmentIds = assignments.stream().map(org.sakaiproject.grading.api.Assignment::getId).collect(Collectors.toList());

        // no need to continue if the site doesn't have gradebook items
        if (assignmentIds.isEmpty()) return Collections.emptyList();

        // collect site information
        return siteService.getOptionalSite(siteId).map(site -> {
            String userId = checkSakaiSession().getUserId();
            Role role = site.getUserRole(userId);
            boolean isMaintainer = StringUtils.equalsIgnoreCase(site.getMaintainRole(), role.getId());

            List<String> userIds = isMaintainer
                    ? site.getRoles().stream()
                            .map(Role::getId)
                            .filter(r -> !site.getMaintainRole().equals(r))
                            .flatMap(r -> site.getUsersHasRole(r).stream())
                            .collect(Collectors.toList())
                    : List.of(userId);

            Map<Long, List<GradeDefinition>> gradeDefinitions = gradingService.getGradesWithoutCommentsForStudentsForItems(siteId, assignmentIds, userIds);

            List<GradeRestBean> beans = new ArrayList<>();
            // collect information for each gradebook item
            for (org.sakaiproject.grading.api.Assignment a : assignments) {
                GradeRestBean bean = new GradeRestBean(a);
                bean.setSiteTitle(site.getTitle());
                bean.setSiteRole(role.getId());

                // collect information for internal gb item
                List<GradeDefinition> gd = gradeDefinitions.get(a.getId());

                if (gd == null) {
                    // no grades for this gb assignment yet
                    bean.setScore("");
                    if (isMaintainer) {
                        bean.setUngraded(userIds.size());
                    }
                    bean.setNotGradedYet(true);
                } else {
                    if (isMaintainer) {
                        double total = 0;
                        for (GradeDefinition d : gd) {
                            if (Objects.equals(GradingConstants.GRADE_TYPE_POINTS, d.getGradeEntryType())) {
                                String grade = d.getGrade();
                                if (!StringUtils.isBlank(grade)) {
                                    total += Double.parseDouble(grade);
                                }
                            }
                        }
                        bean.setScore(total > 0 ? String.format("%.2f", total / gd.size()) : "");
                        bean.setUngraded(userIds.size() - gd.size());
                        bean.setNotGradedYet(gd.isEmpty());
                    } else {
                        if (a.getReleased() && !gd.isEmpty()) {
                            bean.setNotGradedYet(false);
                            bean.setScore(StringUtils.trimToEmpty(gd.get(0).getGrade()));
                        } else {
                            bean.setScore("");
                            bean.setNotGradedYet(true);
                        }
                    }
                }

                String url = "";
                if (a.getExternallyMaintained()) {
                    url = entityManager.getUrl(a.getReference(), Entity.UrlType.PORTAL).orElse("");
                }
                if (StringUtils.isBlank(url)) {
                    ToolConfiguration tc = site.getToolForCommonId("sakai.gradebookng");
                    url = tc != null ? "/portal/directtool/" + tc.getId() : "";
                }
                bean.setUrl(url);

                // add data to list
                beans.add(bean);
            }
            return beans;
        }).orElse(Collections.emptyList());
    };

    @GetMapping(value = "/users/me/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GradeRestBean> getUserGrades() {

        Session session = checkSakaiSession();
        return portalService.getPinnedSites(session.getUserId()).stream()
                .flatMap(s -> gradeDataSupplierForSite.apply(s).stream())
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/sites/{siteId}/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GradeRestBean> getSiteGrades(@PathVariable String siteId) throws UserNotDefinedException {

        checkSakaiSession();

        return gradeDataSupplierForSite.apply(siteId);
    }

    /* Endpoint to retrieve all users grades within a site
        courseGrades param is optional to additionally get the course grades all of users */
    @GetMapping(value = "/sites/{siteId}/users/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleSiteRestBean getUsersGrades(
        @PathVariable String siteId,
        @RequestParam(required = false, defaultValue = "false") boolean courseGrades
    ) {

        checkSakaiSession();

        Optional<Site> optionalSite = siteService.getOptionalSite(siteId);

        if (optionalSite.isPresent()) {
            Site site = optionalSite.get();
            SimpleSiteRestBean simpleSiteRestBean = new SimpleSiteRestBean(site);

            List<UserRestBean> userGradeRestBeanList = new ArrayList<>();

            Set<String> userSet = site.getUsers();
            Set<Member> memberSet = site.getMembers();

            List<org.sakaiproject.grading.api.Assignment> assignments = gradingService.getViewableAssignmentsForCurrentUser(siteId);
            List<Long> assignmentIds = assignments.stream().map(org.sakaiproject.grading.api.Assignment::getId).collect(Collectors.toList());

            Map<Long, List<GradeDefinition>> gradeDefinitions = gradingService.getGradesWithoutCommentsForStudentsForItems(siteId, assignmentIds, List.copyOf(userSet));

            for (Member member : memberSet) {
                UserRestBean userGradeRestBean = new UserRestBean();
                Role role = site.getUserRole(member.getUserId());

                userGradeRestBean.setId(member.getUserId());
                userGradeRestBean.setName(member.getUserDisplayId());

                List<UserGradesRestBean> userGradesRestBean = new ArrayList<>();

                for (org.sakaiproject.grading.api.Assignment a : assignments) {
                    UserGradesRestBean userGradesBean = new UserGradesRestBean(a);

                    GradeRestBean bean = new GradeRestBean(a);
                    bean.setSiteTitle(site.getTitle());
                    bean.setSiteRole(role.getId());

                    List<GradeDefinition> gdList = gradeDefinitions.get(a.getId());

                    if (gdList != null) {
                        Optional<GradeDefinition> optionalGd = gdList.stream()
                            .filter(gd -> gd.getStudentUid().equals(member.getUserId())).findFirst();


                        if (optionalGd.isPresent() && a.getReleased()) {
                            GradeDefinition gd = optionalGd.get();

                            if (gd.getDateRecorded() != null) {
                                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                userGradesBean.setLastGrade(formatter.format(gd.getDateRecorded()));
                            } else {
                                userGradesBean.setLastGrade("");
                            }

                            userGradesBean.setScore(StringUtils.trimToEmpty(gd.getGrade()));
                            userGradesBean.setGraded(true);
                        } else {
                            userGradesBean.setLastGrade("");
                            userGradesBean.setScore("");
                            userGradesBean.setGraded(false);
                        }
                    } else {
                        userGradesBean.setLastGrade("");
                        userGradesBean.setScore("");
                        userGradesBean.setGraded(false);
                    }

                    userGradesRestBean.add(userGradesBean);
                }

                userGradeRestBean.setGrades(userGradesRestBean);
                userGradeRestBeanList.add(userGradeRestBean);
            }

            List<CourseGradeRestBean> courseGradeList = null;

            if (courseGrades) {
                courseGradeList = getStudentCourseGradeList(siteId, userSet);
            }

            simpleSiteRestBean.setCourseGrades(courseGradeList);
            simpleSiteRestBean.setUserGrades(userGradeRestBeanList);

            return simpleSiteRestBean;
        } else {
            return new SimpleSiteRestBean();
        }
    }

    /* Endpoint to retrieve the latest grades from the date provided until today
        - courseGrades param is optional to additionally get the course grades all of users
        - timestamp if not provided will be set 1 week before today */
    @GetMapping(value = "/grades/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SiteLatestGradesBean> getLatestGrades(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime timestamp,
        @RequestParam(required = false, defaultValue = "false") boolean courseGrades
    ) {

        checkSakaiSession();

        String query2 = "SELECT gr.STUDENT_ID, gr.POINTS_EARNED, gr.DATE_RECORDED, gbo.NAME, gbo.POINTS_POSSIBLE, gbo.EXTERNAL_APP_NAME, gb.GRADEBOOK_UID FROM GB_GRADE_RECORD_T gr JOIN GB_GRADABLE_OBJECT_T gbo ON gr.GRADABLE_OBJECT_ID = gbo.ID JOIN GB_GRADEBOOK_T gb ON gbo.GRADEBOOK_ID = gb.ID WHERE gr.date_recorded > ?;";

        if (timestamp == null) {
            timestamp = LocalDateTime.now().minus(1, ChronoUnit.WEEKS);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = timestamp.format(formatter);

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = sqlService.borrowConnection();
            conn.setReadOnly(true);
            ps = conn.prepareStatement(query2, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(formattedDate));

            rs = ps.executeQuery();

            Map<String, Map<String, UserLatestGradeBean>> siteGradesMap = new HashMap<>();

            while (rs.next()) {
                String studentId = rs.getString("STUDENT_ID");
                Double pointsEarned = rs.getDouble("POINTS_EARNED");
                Double pointsPossible = rs.getDouble("POINTS_POSSIBLE");

                Timestamp dateRecordedTimestamp = rs.getTimestamp("DATE_RECORDED");
                String formattedDateRecorded = null;
                if (timestamp != null) {
                    LocalDateTime dateRecorded = dateRecordedTimestamp.toLocalDateTime();
                    formattedDateRecorded = dateRecorded.format(formatter);
                }

                String gradebookUid = rs.getString("GRADEBOOK_UID");
                String gradableTitle = rs.getString("NAME");
                String toolId = rs.getString("EXTERNAL_APP_NAME");

                AssesmentGradeRestBean assesmentGradeRestBean = new AssesmentGradeRestBean();
                //assesmentGradeRestBean.setDisplayGrade(assesmentGradeRestBean.getDisplayGrade());
                assesmentGradeRestBean.setToolId(toolId);
                assesmentGradeRestBean.setTotalPointsPossible(pointsPossible);
                assesmentGradeRestBean.setPoints(pointsEarned);
                //assesmentGradeRestBean.setPercentageGrade(String.format("%.2f%%", (pointsEarned / pointsPossible) * 100));
                assesmentGradeRestBean.setGradableTitle(gradableTitle);
                assesmentGradeRestBean.setDateRecorded(formattedDateRecorded);

                Map<String, UserLatestGradeBean> userGradesMap = siteGradesMap.computeIfAbsent(gradebookUid, k -> new HashMap<>());
                UserLatestGradeBean userLatestGradeBean = userGradesMap.computeIfAbsent(studentId, k -> {
                    UserLatestGradeBean newUserBean = new UserLatestGradeBean();
                    newUserBean.setStudentId(studentId);
                    newUserBean.setAssesmentGradeRestBeanList(new ArrayList<>());
                    return newUserBean;
                });

                userLatestGradeBean.getAssesmentGradeRestBeanList().add(assesmentGradeRestBean);

                CourseGradeRestBean courseGradeList = null;
                if (courseGrades) {
                    courseGradeList = getStudentCourseGrade(gradebookUid, studentId);
                }

                userLatestGradeBean.setCourseGradeRestBean(courseGradeList);
            }

            List<SiteLatestGradesBean> siteLatestGradesList = new ArrayList<>();
            for (Map.Entry<String, Map<String, UserLatestGradeBean>> siteEntry : siteGradesMap.entrySet()) {
                String siteId = siteEntry.getKey();
                Map<String, UserLatestGradeBean> userGradesMap = siteEntry.getValue();

                Site site = siteService.getSite(siteId);
                String courseTitle = site.getTitle();
                String courseStartDate = site.getProperties().getProperty("publish_date");
                String courseEndDate = site.getProperties().getProperty("unpublish_date");

                String formattedStartDate = OffsetDateTime.parse(courseStartDate)
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                String formattedEndDate = OffsetDateTime.parse(courseEndDate)
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                List<UserLatestGradeBean> userLatestGradeBeans = new ArrayList<>(userGradesMap.values());

                userLatestGradeBeans.sort(Comparator.comparing(UserLatestGradeBean::getStudentId));

                SiteLatestGradesBean siteLatestGradesBean = new SiteLatestGradesBean();
                siteLatestGradesBean.setId(siteId);
                siteLatestGradesBean.setCourseTitle(courseTitle);
                siteLatestGradesBean.setCourseStartDate(formattedStartDate);
                siteLatestGradesBean.setCourseEndDate(formattedEndDate);
                siteLatestGradesBean.setUserLatestGradeList(userLatestGradeBeans);

                siteLatestGradesList.add(siteLatestGradesBean);
            }
            return siteLatestGradesList;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // Endpoint to retrieve the course grades of all users within a site
    @GetMapping(value = "/sites/{siteId}/course/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<CourseGradeRestBean>> getStudentsCourseGrade(
        @PathVariable String siteId
    ) {
        try {
            Site site = siteService.getSite(siteId);
            List<CourseGradeRestBean> courseGradeList = getStudentCourseGradeList(siteId, site.getUsers());

            return new HashMap<>() {{ put(siteId, courseGradeList); }};

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private List<CourseGradeRestBean> getStudentCourseGradeList(String siteId, Set<String> userSet) {
        List<CourseGradeRestBean> courseGradeList = new ArrayList<>();
        Map<String, CourseGradeTransferBean> studentsCourseGrade = gradingService.getCourseGradeForStudents(siteId, userSet.stream().collect(Collectors.toList()));

        for (Map.Entry<String, CourseGradeTransferBean> entry : studentsCourseGrade.entrySet()) {
            String userId = entry.getKey();
            CourseGradeTransferBean courseGradeTransferBean = entry.getValue();

            CourseGradeRestBean courseGradeRestBean = buildCourseGradeRestBean(userId, courseGradeTransferBean);

            courseGradeList.add(courseGradeRestBean);
        }

        return courseGradeList;
    }

    private CourseGradeRestBean getStudentCourseGrade(String siteId, String studentId) {
        try {
            Map<String, CourseGradeTransferBean> studentCourseGrades = gradingService.getCourseGradeForStudents(siteId, List.of(studentId));

            CourseGradeTransferBean courseGradeTransferBean = studentCourseGrades.get(studentId);
            if (courseGradeTransferBean != null) {
                return new CourseGradeRestBean(studentId, courseGradeTransferBean);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return null;
    }

    private CourseGradeRestBean buildCourseGradeRestBean(String userId, CourseGradeTransferBean courseGradeTransferBean) {
        if (userId != null && courseGradeTransferBean != null) {
            return new CourseGradeRestBean(userId, courseGradeTransferBean);
        } else {
            return null;
        }
    }
}

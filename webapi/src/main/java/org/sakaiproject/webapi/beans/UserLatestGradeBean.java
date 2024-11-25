package org.sakaiproject.webapi.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserLatestGradeBean {

	private String studentId;
	private List<AssesmentGradeRestBean> assesmentGradeRestBeanList;
	private CourseGradeRestBean courseGradeRestBean;
	
}
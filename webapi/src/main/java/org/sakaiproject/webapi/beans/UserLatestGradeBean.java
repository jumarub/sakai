package org.sakaiproject.webapi.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserLatestGradeBean {

	private String studentId;
	private String score;
	private String dateRecorded;
	private String toolId;
	private String gradableTitle;
	
}

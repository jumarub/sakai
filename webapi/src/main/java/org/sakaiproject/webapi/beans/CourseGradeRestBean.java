package org.sakaiproject.webapi.beans;

import org.sakaiproject.grading.api.CourseGradeTransferBean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CourseGradeRestBean {

	private String studentId;
	private String displayGrade;
	private Double totalPointsPossible;
	private Double points;
	private String percentageGrade;

	public CourseGradeRestBean(String studentId, CourseGradeTransferBean courseGradeTransferBean) {
		this.studentId = studentId;
		this.displayGrade = courseGradeTransferBean.getDisplayGrade();
		this.totalPointsPossible = courseGradeTransferBean.getTotalPointsPossible();
		this.points = courseGradeTransferBean.getPointsEarned();
		this.percentageGrade = courseGradeTransferBean.getCalculatedGrade();
	}

}

package org.sakaiproject.webapi.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssesmentGradeRestBean {

    //private String displayGrade;
    private String toolId;;
    private Double totalPointsPossible;
    private Double points;
    //private String percentageGrade;
    private String gradableTitle;
    private String dateRecorded;

}

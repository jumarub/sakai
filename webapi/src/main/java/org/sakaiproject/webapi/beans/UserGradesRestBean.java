package org.sakaiproject.webapi.beans;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.grading.api.Assignment;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.webapi.beans.SiteEntityRestBean.SiteEntityType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserGradesRestBean {
	@EqualsAndHashCode.Include
	private Long entityId;
	private SiteEntityType type;
	private String title;
	private String score;
	private boolean isGraded;
	private String lastGrade;

	public UserGradesRestBean(Assignment assignment) {
		this.entityId = assignment.getId();
		this.type = SiteEntityType.ASSIGNMENT;
		this.title = assignment.getName();
	}

}

package org.sakaiproject.webapi.beans;

import java.util.List;

import org.sakaiproject.site.api.Site;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SimpleSiteRestBean {
	@EqualsAndHashCode.Include
	private String id;
	private String title;
	private List<CourseGradeRestBean> courseGrades;
	private List<UserRestBean> userGrades;

	public SimpleSiteRestBean(Site site) {
		this.id = site.getId();
		this.title = site.getTitle();
	}

}

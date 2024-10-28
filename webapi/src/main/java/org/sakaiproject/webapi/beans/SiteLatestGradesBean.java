package org.sakaiproject.webapi.beans;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SiteLatestGradesBean {
	@EqualsAndHashCode.Include
	private String id;
	private List<UserLatestGradeBean> userLatestGradeList;
}

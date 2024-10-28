package org.sakaiproject.webapi.beans;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserRestBean {
	@EqualsAndHashCode.Include
	private String id;
	private String name;
	private List<UserGradesRestBean> grades;
}

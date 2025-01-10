package org.sakaiproject.webapi.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateRestBean {
	
	private String id;
	private String title;
	private String description;
	private String publishDate;
	private String unpublishDate;

}

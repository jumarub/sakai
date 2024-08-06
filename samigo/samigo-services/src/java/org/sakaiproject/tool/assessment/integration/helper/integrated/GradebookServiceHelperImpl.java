/**********************************************************************************
 * Copyright (c) 2004, 2005, 2006, 2007, 2008, 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.tool.assessment.integration.helper.integrated;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Locale;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.math3.util.Precision;

import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.grading.api.AssessmentNotFoundException;
import org.sakaiproject.grading.api.GradingService;
import org.sakaiproject.grading.api.model.Gradebook;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.assessment.data.dao.assessment.PublishedAssessmentData;
import org.sakaiproject.tool.assessment.data.dao.grading.AssessmentGradingData;
import org.sakaiproject.tool.assessment.data.ifc.assessment.PublishedAssessmentIfc;
import org.sakaiproject.tool.assessment.facade.AgentFacade;
import org.sakaiproject.tool.assessment.facade.GradebookFacade;
import org.sakaiproject.tool.assessment.integration.helper.ifc.GradebookServiceHelper;
import org.sakaiproject.tool.assessment.services.assessment.PublishedAssessmentService;
import org.sakaiproject.user.api.PreferencesService;
/**
 *
 * <p>Description:
 * This is an integrated context implementation helper delegate class for
 * the GradebookService class.
 * "Integrated" means that Samigo (Tests and Quizzes)
 * is running within the context of the Sakai portal and authentication
 * mechanisms, and therefore makes calls on Sakai for things it needs.</p>
 * <p>Note: To customize behavior you can add your own helper class to the
 * Spring injection via the integrationContext.xml for your context.
 * The particular integrationContext.xml to be used is selected by the
 * build process.
 * </p>
 * <p>Sakai Project Copyright (c) 2005</p>
 * <p> </p>
 * @author Ed Smiley <esmiley@stanford.edu>
 */
@Slf4j
public class GradebookServiceHelperImpl implements GradebookServiceHelper
{

	private SecurityService securityService = (SecurityService) ComponentManager.get(SecurityService.class);
	private SessionManager sessionManager = (SessionManager) ComponentManager.get(SessionManager.class);
	private SiteService siteService = (SiteService) ComponentManager.get(SiteService.class);
	private PreferencesService preferencesService = (PreferencesService) ComponentManager.get(PreferencesService.class);

	private Site getCurrentSite(String id) {
		Site site = null;
		try {
			site = siteService.getSite(id);
		} catch (IdUnusedException e) {
			log.error(e.getMessage());
		}
		return site;
	}
	
   /**
    * Remove a published assessment from the gradebook.
    * @param gradebookUId the gradebook id
    * @param g  the Gradebook Service
    * @param publishedAssessmentId the id of the published assessment
    * @throws java.lang.Exception
    */
    public void removeExternalAssessment(String gradebookUId, String publishedAssessmentId, GradingService g)
        throws Exception {
        g.removeExternalAssignment(null, publishedAssessmentId, getAppName());
    }

  public boolean isAssignmentDefined(String assessmentTitle,
		  GradingService g)
  {
    String gradebookUId = GradebookFacade.getGradebookUId();
    return g.isAssignmentDefined(gradebookUId, gradebookUId, assessmentTitle);
  }
  
  public String getAppName()
  {
      return "sakai.samigo";
  }

  /**
   * Add a published assessment to gradebook.
   * @param publishedAssessment the published assessment
   * @param g  the Gradebook Service
   * @return false: cannot add to gradebook
   * @throws java.lang.Exception
   */
  public boolean addToGradebook(String gradebookUId, PublishedAssessmentData publishedAssessment, Long categoryId,
		  GradingService g) throws
    Exception
  {
    boolean added = false;
    String siteId = GradebookFacade.getGradebookUId();
    if (gradebookUId == null)
    {
      return false;
    }

    String title = StringEscapeUtils.unescapeHtml4(publishedAssessment.getTitle());
    // TODO S2U-26 si son varios gUid se hace bucle y 1 llamada para cada
    if(!g.isAssignmentDefined(gradebookUId, siteId, title))
    {
      g.addExternalAssessment(gradebookUId, siteId,
              publishedAssessment.getPublishedAssessmentId().toString(),
              null,
              title,
              publishedAssessment.getTotalScore(),
              publishedAssessment.getAssessmentAccessControl().getDueDate(),
              getAppName(), // Use the app name from sakai
              null,
              false,
              categoryId,
              publishedAssessment.getReference());
      added = true;
    }
    return added;
  }

  public boolean buildItemToGradebook(PublishedAssessmentData publishedAssessment,
    List<String> selectedGroups, Long categoryId, GradingService g) throws Exception {

    boolean isGradebookGroupEnabled = g.isGradebookGroupEnabled(AgentFacade.getCurrentSiteId());

    if (isGradebookGroupEnabled) {
      List<Gradebook> gbList = g.getGradebookGroupInstances(AgentFacade.getCurrentSiteId());
      List<String> existingGradebookUids = gbList.stream().map(Gradebook::getUid).collect(Collectors.toList());

      List<String> selectedGradebookUids = new ArrayList<>();

      if (existingGradebookUids.containsAll(selectedGroups)) {
        selectedGradebookUids.addAll(selectedGroups);
      } else {
        System.out.println();
      }

      for (String gUid : selectedGradebookUids) {
        addToGradebook(gUid, publishedAssessment, (categoryId != null ? categoryId : publishedAssessment.getCategoryId()), g);
      }

      return true;
    } else {
      System.out.println("NO TENGO GRUPOS DE GRADEBOOK: " + GradebookFacade.getGradebookUId());
      return addToGradebook(GradebookFacade.getGradebookUId(), publishedAssessment,
        (categoryId != null ? categoryId : publishedAssessment.getCategoryId()), g);
    }
  }

  /**
   * Update a gradebook.
   * @param publishedAssessment the published assessment
   * @param g  the Gradebook Service
   * @return false: cannot update the gradebook
   * @throws java.lang.Exception
   */
  public boolean updateGradebook(PublishedAssessmentIfc publishedAssessment,
		  GradingService g) throws Exception
  {
    log.debug("updateGradebook start");
    // TODO S2U-26 uno de estos sera distinto, depende de como guardemos aqui
    String gradebookUId = GradebookFacade.getGradebookUId();
    String siteId = GradebookFacade.getGradebookUId();
    if (gradebookUId == null)
    {
      return false;
    }

    log.debug("before g.updateExternalAssessment()");
	g.updateExternalAssessment(gradebookUId,
            publishedAssessment.getPublishedAssessmentId().toString(),
            null,
            null,
            publishedAssessment.getTitle(),
            null,
            publishedAssessment.getTotalScore(),
            publishedAssessment.getAssessmentAccessControl().getDueDate(),
            null);
    return true;
  }

  /**
   * Update the grading of the assessment.
   * @param ag the assessment grading.
   * @param g  the Gradebook Service
   * @throws java.lang.Exception
   */
  public void updateExternalAssessmentScore(AssessmentGradingData ag, GradingService g) throws Exception {
    updateExternalAssessmentScore(ag, g, null);
  }

  /**
   * Update the grading of the assessment.
   * @param ag the assessment grading.
   * @param g  the Gradebook Service
   * @param assignmentId the Id of the gradebook assignment.
   * @throws java.lang.Exception
   */
  public void updateExternalAssessmentScore(AssessmentGradingData ag, GradingService g, Long assignmentId) throws Exception {
    boolean testErrorHandling=false;
    PublishedAssessmentService pubService = new PublishedAssessmentService();

    String gradebookUId = pubService.getPublishedAssessmentOwner(
            ag.getPublishedAssessmentId());
    if (gradebookUId == null)
    {
      return;
    }
    
    //Will pass to null value when last submission is deleted
    String points = null;
    if(ag.getFinalScore() != null) {
        //SAM-1562 We need to round the double score and covert to a double -DH
        double fScore = Precision.round(ag.getFinalScore(), 2);
        Double score = Double.valueOf(fScore).doubleValue();
        points = getFormattedScore(score, gradebookUId);
        log.debug("rounded:  " + ag.getFinalScore() + " to: " + score.toString() );
    }

    if (assignmentId == null) {
      g.updateExternalAssessmentScore(gradebookUId, ag.getPublishedAssessmentId().toString(), ag.getAgentId(), points);
    } else {

        // This is the student grading it's own submission, we need to grant permissions to the student temporarily.
        SecurityAdvisor securityAdvisor = new SecurityAdvisor() {
            public SecurityAdvice isAllowed(String userId, String function, String reference) {
                return "gradebook.gradeAll".equals(function) ? SecurityAdvice.ALLOWED : SecurityAdvice.PASS;
            }
        };

        try {
            securityService.pushAdvisor(securityAdvisor);
            g.setAssignmentScoreString(gradebookUId, gradebookUId, assignmentId, ag.getAgentId(), points, null);
        } catch (Exception e) {
            log.error("Error while grading submission {} for agent {}", assignmentId, ag.getAgentId());
        } finally {
            securityService.popAdvisor(securityAdvisor);
        }

    }
    if (testErrorHandling){
      throw new Exception("Encountered an error in update ExternalAssessmentScore.");
    }
  }
  
  public void updateExternalAssessmentComment(Long publishedAssessmentId, String studentUid, String comment,
          GradingService g) throws Exception {
	  boolean testErrorHandling=false;
	  PublishedAssessmentService pubService = new PublishedAssessmentService();
	  // TODO S2U-26 uno de estos sera distinto, depende de como guardemos aqui
	  String gradebookUId = pubService.getPublishedAssessmentOwner(publishedAssessmentId);
	  String siteId = gradebookUId;
	  if (gradebookUId == null) {
		  return;
	  }	
	  g.updateExternalAssessmentComment(gradebookUId, siteId, publishedAssessmentId.toString(), studentUid, comment);

	  if (testErrorHandling){
          throw new Exception("Encountered an error in update ExternalAssessmentComment.");
	  }
  }


	public Long getExternalAssessmentCategoryId(String gradebookUId,
        String publishedAssessmentId, GradingService g) {
        try {
            return g.getExternalAssessmentCategoryId(gradebookUId, publishedAssessmentId);
        }
        catch (AssessmentNotFoundException e) {
            log.info("No category defined for publishedAssessmentId={} in gradebookUid={}", publishedAssessmentId, gradebookUId);
        }
		return null;
	}

  private String getFormattedScore(Double score, String gradebookUId) {// TODO S2U-26 ojo cuidado aqui
    String currentLocaleStr = null;
    String userId = AgentFacade.getEid();

    try {
      Site site = siteService.getSite(gradebookUId);
      ResourceProperties siteProperties = site.getProperties();
      currentLocaleStr = (String) siteProperties.get("locale_string");

    } catch (IdUnusedException ex) {
      log.error("Not posible to get siteProperties");
    }

    if (currentLocaleStr == null && userId != null) {
      currentLocaleStr = preferencesService.getLocale(userId).toString();
    }

    if (currentLocaleStr == null) {
      currentLocaleStr = Locale.ENGLISH.toString();
    }

    String[] localeParts = new String[]{"", ""};
    List localePartsList = Arrays.asList(currentLocaleStr.split("_"));
    for (int i = 0; i < localePartsList.size(); i++) {
      localeParts[i] = localePartsList.get(i).toString();
    }

    NumberFormat nf = NumberFormat.getInstance(new Locale(localeParts[0], localeParts[1]));
    return nf.format(score);
  }

}

package com.redhat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.UserTaskService;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.task.impl.command.CommandBasedTaskService;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.helper.FluentKieModuleDeploymentHelper;
import org.kie.api.builder.helper.KieModuleDeploymentHelper;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.google.common.collect.Lists;

@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
@ActiveProfiles("test")
public class BusinessAdminTests extends AbstractJUnit4SpringContextTests {

	private static String GROUP_ID = "com.rhc";
	private static String ARTIFACT_ID = "bpms-knowledge";
	private static String VERSION = "1.0.9-SNAPSHOT";
	private static String simpleProcess = "com.rhc.SimpleProcess";
	
	protected static final DeploymentUnit DEPLOYMENT_UNIT = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);

	@Autowired
	protected ProcessService processService;
	@Autowired
	protected RuntimeDataService runtimeDataService;
	@Autowired
	protected DeploymentService deploymentService;
	@Autowired
	protected UserTaskService userTaskService;
	
	@Autowired
	protected TaskService service;

	@Test
	public void shouldClaimTaskAndDelegateToAnotherUserViaBusinessAdmin() throws InterruptedException {
		Assert.assertNotNull(service);
		CommandBasedTaskService commandBasedTaskService = ((CommandBasedTaskService) service);
		Assert.assertNotNull(commandBasedTaskService);
		
		deploymentService.deploy(DEPLOYMENT_UNIT);

		Map<String, Object> map = new HashMap<>();

		processService.startProcess(DEPLOYMENT_UNIT.getIdentifier(), simpleProcess);
		
		List<TaskSummary> groupPotentialTasks = runtimeDataService.getTasksAssignedAsPotentialOwner("task-group", null);
		Assert.assertEquals( 1, groupPotentialTasks.size() );
		
		userTaskService.claim( groupPotentialTasks.get(0).getId(), "jholmes");
		List<TaskSummary> taskOwnedByJholmes = runtimeDataService.getTasksOwned( "jholmes", null );
		Assert.assertEquals( 1, taskOwnedByJholmes.size() );
		
		List<TaskSummary> groupPotentialTasksAfterClaim = runtimeDataService.getTasksAssignedAsPotentialOwner("task-group", null);
		Assert.assertEquals( 0, groupPotentialTasksAfterClaim.size() );
		
		List<TaskSummary> hackedTasks = commandBasedTaskService.getTasksByVariousFields("dude", null, null, null, null, Arrays.asList("task-group"), null, null, null, false);
		Assert.assertEquals(1, hackedTasks.size());
		Assert.assertEquals("jholmes", hackedTasks.get(0).getActualOwnerId());
		
		userTaskService.delegate( hackedTasks.get(0).getId(), "jholmes", "dude");
		List<TaskSummary> taskOwnedByJholmesAfterDeleagte = runtimeDataService.getTasksOwned( "jholmes", null );
		Assert.assertEquals( 0, taskOwnedByJholmesAfterDeleagte.size() );
		
		List<TaskSummary> taskOwnedByDudeAfterDelegate = runtimeDataService.getTasksOwned( "dude", null );
		Assert.assertEquals( 1, taskOwnedByDudeAfterDelegate.size() );
		
		List<TaskSummary> hackedTasksAfterDelegate = commandBasedTaskService.getTasksByVariousFields("dude", null, null, null, null, Arrays.asList("task-group"), null, null, null, false);
		Assert.assertEquals(1, hackedTasks.size());
		Assert.assertEquals("dude", hackedTasksAfterDelegate.get(0).getActualOwnerId());
	}
	
	@BeforeClass
	public static void generalSetup() {
		TestUtils.setupPoolingDataSource();
		
		FluentKieModuleDeploymentHelper helper1 = KieModuleDeploymentHelper.newFluentInstance();
		TestUtils.createDefaultKieBase(helper1);
		helper1.setGroupId(GROUP_ID).setArtifactId(ARTIFACT_ID).setVersion(VERSION).addResourceFilePath("com/rhc/SimpleProcess.bpmn2").createKieJarAndDeployToMaven();
		
	}
}

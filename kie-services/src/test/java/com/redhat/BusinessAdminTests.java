package com.redhat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.UserTaskService;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.task.commands.GetTasksByVariousFieldsCommand;
import org.jbpm.services.task.impl.command.CommandBasedTaskService;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.helper.FluentKieModuleDeploymentHelper;
import org.kie.api.builder.helper.KieModuleDeploymentHelper;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.redhat.command.CustomCommand;

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
	
	/**
	 * This is not the public API we should be using. It's linked to my "hacked" tasks
	 */
	@Autowired
	protected TaskService service;
	protected CommandBasedTaskService commandBasedTaskService = ((CommandBasedTaskService) service);
	
	@Test
	public void shouldClaimTaskAndDelegateToAnotherUserViaBusinessAdmin() throws InterruptedException {		
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
	
	@Test
	public void shouldExecuteACustomCommand(){
		if( !deploymentService.isDeployed(DEPLOYMENT_UNIT.getIdentifier()) ){
			deploymentService.deploy(DEPLOYMENT_UNIT);
		}
		
		processService.startProcess(DEPLOYMENT_UNIT.getIdentifier(), simpleProcess);
		
		List<TaskSummary> groupPotentialTasks = runtimeDataService.getTasksAssignedAsPotentialOwner("task-group", null);
		Assert.assertEquals( 1, groupPotentialTasks.size() );
		
		userTaskService.claim( groupPotentialTasks.get(0).getId(), "jholmes");
		List<TaskSummary> taskOwnedByJholmes = runtimeDataService.getTasksOwned( "jholmes", null );
		Assert.assertEquals( 1, taskOwnedByJholmes.size() );
		
		List<TaskSummary> groupPotentialTasksAfterClaim = runtimeDataService.getTasksAssignedAsPotentialOwner("task-group", null);
		Assert.assertEquals( 0, groupPotentialTasksAfterClaim.size() );
		
		/**
		 * Functionally equivalent to line 84
		 */
		GetTasksByVariousFieldsCommand command = new GetTasksByVariousFieldsCommand( null, null, null, null, Arrays.asList("task-group"), null, null, null, false);
		command.setUserId("dude");
		List<TaskSummary> hackedTasks = userTaskService.execute(DEPLOYMENT_UNIT.getIdentifier(), command);
		Assert.assertEquals(1, hackedTasks.size());
		Assert.assertEquals("jholmes", hackedTasks.get(0).getActualOwnerId());
		
		/**
		 * Functionally equivalent to the above
		 */
		CustomCommand customCommand = new CustomCommand("task-group");
		command.setUserId("dude");
		List<TaskSummary> customTasks = userTaskService.execute(DEPLOYMENT_UNIT.getIdentifier(), customCommand);
		Assert.assertEquals(1, customTasks.size());
		Assert.assertEquals("jholmes", customTasks.get(0).getActualOwnerId());
		
	}
	
	@BeforeClass
	public static void generalSetup() {
		TestUtils.setupPoolingDataSource();
		
		FluentKieModuleDeploymentHelper helper1 = KieModuleDeploymentHelper.newFluentInstance();
		TestUtils.createDefaultKieBase(helper1);
		helper1.setGroupId(GROUP_ID).setArtifactId(ARTIFACT_ID).setVersion(VERSION).addResourceFilePath("com/rhc/SimpleProcess.bpmn2").createKieJarAndDeployToMaven();
		
	}
}

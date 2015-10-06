package com.redhat;

import java.util.HashMap;
import java.util.Map;

import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.UserTaskService;
import org.jbpm.services.api.model.DeploymentUnit;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.helper.FluentKieModuleDeploymentHelper;
import org.kie.api.builder.helper.KieModuleDeploymentHelper;
import org.kie.api.task.TaskService;
import org.kie.internal.query.QueryContext;
import org.kie.internal.query.QueryFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import bitronix.tm.resource.jdbc.PoolingDataSource;

@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
@ActiveProfiles("test")
public class BpmTest extends AbstractJUnit4SpringContextTests {

	protected static final String GROUP_ID = "com.rhc";
	protected static final String ARTIFACT_ID = "bpms-knowledge";
	protected static final String VERSION = "1.0.6-SNAPSHOT";
	protected static final DeploymentUnit DEPLOYMENT_UNIT = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);
	protected static final String PROCESS_ID = "com.rhc.SimpleTask"; // TODO this might be different for you

	@Autowired
	protected ProcessService processService;
	@Autowired
	protected RuntimeDataService runtimeDataService;
	@Autowired
	protected DeploymentService deploymentService;
	@Autowired
	protected UserTaskService userTaskService;

	@Test
	public void test() throws InterruptedException {
		deploymentService.deploy(DEPLOYMENT_UNIT);

		Map<String, Object> map = new HashMap<>();
		map.put("StringVar", new String("test"));

		processService.startProcess(DEPLOYMENT_UNIT.getIdentifier(), PROCESS_ID, map);
	}
	
	@Test
	public void newTest(){
		deploymentService.deploy(DEPLOYMENT_UNIT);
		
		runtimeDataService.getTasksAssignedAsBusinessAdministrator("jholmes", null);
		
		Assert.assertEquals(1, runtimeDataService.getProcessesByDeploymentId(DEPLOYMENT_UNIT.getIdentifier(), null).size() );
	}

	protected static PoolingDataSource pds;

	@BeforeClass
	public static void generalSetup() {
		TestUtils.setupPoolingDataSource();
		/*
		FluentKieModuleDeploymentHelper helper1 = KieModuleDeploymentHelper.newFluentInstance();
		TestUtils.createDefaultKieBase(helper1);
		helper1.setGroupId(GROUP_ID).setArtifactId(ARTIFACT_ID).setVersion(VERSION).addResourceFilePath("com/redhat/simple/Process.bpmn2").createKieJarAndDeployToMaven();
		*/
	}
}

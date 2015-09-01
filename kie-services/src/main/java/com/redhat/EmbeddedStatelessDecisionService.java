package com.redhat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.event.rule.DebugRuleRuntimeEventListener;
import org.kie.api.logger.KieRuntimeLogger;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedStatelessDecisionService implements StatelessDecisionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedStatelessDecisionService.class);

	private KieCommands commandFactory;
	private KieContainer kieContainer;
	private String auditLogName;
	private boolean debugConsoleLogging;
	private boolean inited = false;

	public EmbeddedStatelessDecisionService(String auditLogName, boolean debugConsoleLogging) {
		this.auditLogName = auditLogName;
		this.debugConsoleLogging = debugConsoleLogging;
		init();
	}

	/**
	 * use the default constructor only with spring contexts. be use sure to use
	 * the init-method
	 */
	public EmbeddedStatelessDecisionService() {
	}

	public void init() {
		kieContainer = KieServices.Factory.get().getKieClasspathContainer();
		Results results = kieContainer.verify();
		if ( results.getMessages().size() > 0 ){
			LOGGER.error("there are compilation errors with your rules or processes!");
			for ( Message message : results.getMessages() ){
				LOGGER.error(message.toString());
			}
		}

		/**
		 * Break point here to find what rules are in the KIE Base
		 */
		commandFactory = KieServices.Factory.get().getCommands();
		
		inited = true;
	}

	@Override
	public <Response> Response runRules(Collection<Object> facts, String processId, Class<Response> responseClazz) {
		LOGGER.debug("begin execution");
		if ( !inited ){
			init();
		}
		StatelessKieSession session;
		try {
			session = kieContainer.newStatelessKieSession("stateless");
		} catch (Exception e) {
			LOGGER.error("The KieContainer is empty; Upgrade the KieContainer to a valid KieModule to fire rules");
			return null;
		}

		BatchExecutionCommand batchExecutionCommand = createBatchExecutionCommand(facts, processId, responseClazz);
		RuleListener ruleListener = new RuleListener();
		KieRuntimeLogger auditLogger = null;

		// only use in test situations
		if (auditLogName != null) {
			auditLogger = KieServices.Factory.get().getLoggers().newFileLogger(session, auditLogName);
		}

		// only use in test situations
		if (debugConsoleLogging) {
			session.addEventListener(new DebugRuleRuntimeEventListener());
			session.addEventListener(new DebugAgendaEventListener());
		}

		// this is used capture the enrichments run in the service
		String fieldName = ReflectiveExecutionResultsTransformer.getRuleListenerFieldNameOnResponseClass(responseClazz);
		if (fieldName != null) {
			LOGGER.debug("response class has a rule listener field. adding a rule listener to the rule session.");
			session.addEventListener(ruleListener);
		} else {
			LOGGER.debug("response class does not have a rule listener field. no listener created.");
		}

		ExecutionResults results = session.execute(batchExecutionCommand);

		Response response = ReflectiveExecutionResultsTransformer.transform(results, responseClazz, ruleListener, fieldName);

		if (auditLogger != null) {
			auditLogger.close();
		}

		return response;
	}

	public BatchExecutionCommand createBatchExecutionCommand(Collection<Object> facts, String processId, Class<?> responseClazz) {
		List<Command<?>> commands = new ArrayList<Command<?>>();

		if (facts != null) {
			commands.add(commandFactory.newInsertElements(facts));
		}
		if (processId != null && !processId.isEmpty()) {
			commands.add(commandFactory.newStartProcess(processId));
		}

		commands.add(commandFactory.newFireAllRules());

		// creates commands to run the queries at the end of process
		commands.addAll(QueryUtils.buildQueryCommands(responseClazz));

		return commandFactory.newBatchExecution(commands);
	}

	public KieContainer getKieContainer() {
		return kieContainer;
	}

	@Override
	public boolean createOrUpgradeRulesWithVersion(String group, String artifact, String version) {
		ReleaseId releaseId = KieServices.Factory.get().newReleaseId(group, artifact, version);
		Results results = null;
		try {
			results = kieContainer.updateToVersion(releaseId);
		} catch (UnsupportedOperationException e) {
			LOGGER.info("Upgrading to version " + releaseId.toString());
			try {
				kieContainer = KieServices.Factory.get().newKieContainer(releaseId);
				results = kieContainer.updateToVersion(releaseId);
			} catch (Exception e2) {
				return false;
			}
		}

		return results.getMessages().size() == 0;
	}
	
	@Override
	public String getCurrentRulesVersion() {
		ReleaseId version = this.kieContainer.getReleaseId();
		if ( version == null){
			return "local container is empty";
		}
		return version.toString();
	}

	public String getAuditLogName() {
		return auditLogName;
	}

	public void setAuditLogName(String auditLogName) {
		this.auditLogName = auditLogName;
	}

	public boolean isDebugConsoleLogging() {
		return debugConsoleLogging;
	}

	public void setDebugConsoleLogging(boolean debugConsoleLogging) {
		this.debugConsoleLogging = debugConsoleLogging;
	}

}

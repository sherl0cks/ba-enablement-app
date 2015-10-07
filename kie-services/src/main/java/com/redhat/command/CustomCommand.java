package com.redhat.command;

import static org.kie.internal.query.QueryParameterIdentifiers.POTENTIAL_OWNER_ID_LIST;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.services.task.commands.TaskContext;
import org.jbpm.services.task.commands.UserGroupCallbackTaskCommand;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.command.Context;

public class CustomCommand extends UserGroupCallbackTaskCommand<List<TaskSummary>> {
	
	private String potentialOwner;
	
	public CustomCommand(String potentialOwner) {
		this.potentialOwner = potentialOwner;
	}

	private static final long serialVersionUID = -3918676398804147761L;

	public List<TaskSummary> execute(Context cntxt) {
	    TaskContext context = (TaskContext) cntxt;
        
	    Map<String, List<?>> params = new HashMap<String, List<?>>();
        List<String> potentialOwners = Arrays.asList(potentialOwner);
        params.put(POTENTIAL_OWNER_ID_LIST, potentialOwners);
        
        /**
         * If I wanted to something custom, I could use the TaskPersistanceContext to access JPA directly
         * context.getPersistenceContext().queryStringWithParametersInTransaction(queryString, params, clazz);
         * context.getTaskQueryService().query(potentialOwner, queryData); 
         */
           
        
        return context.getTaskQueryService().getTasksByVariousFields(userId, params, false);
    }
}

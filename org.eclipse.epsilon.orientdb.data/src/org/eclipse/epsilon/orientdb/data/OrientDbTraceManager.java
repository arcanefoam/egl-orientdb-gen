/*******************************************************************************
 * Copyright (c) 2016 University of York
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jonathan Co - Initial API and implementation
 *******************************************************************************/
package org.eclipse.epsilon.orientdb.data;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.epsilon.orientdb.business.trace.*;
import org.eclipse.epsilon.orientdb.data.execute.TraceOrientDbDAO;
import org.eclipse.epsilon.orientdb.data.trace.*;
import org.eclipse.epsilon.orientdb.data.trace.impl.*;

import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.frames.FramedTransactionalGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;

/**
 * Implementation of {@link IExecutionTraceManager} that uses Orient DB as its underlying
 * database.
 * 
 * @author Jonathan Co
 *
 */
public class OrientDbTraceManager implements IExecutionTraceManager {
	
	private OrientGraphFactory factory;
	
	/**
	 * The execution context id of this trace manager. We keep a reference to the id because all operations are atomic
	 * and hence keeping the vertex reference will result in it being detached in future operations.
	 * 
	 * If not unique for a session, the {@link OrientDbTraceManager#setExecutionContext(String, Collection)} 
	 * method can be invoked before creating or retrieving records form the database.
	 * 
	 */
	private Object executionContextId;

	private String url;

	private String user;

	private String password;

	private TraceOrientDbDAO orientDbDAO;

	private boolean setup;
	
	/**
	 * Set the configuration parameters for the OrientDB. The first three array elements should be the url, user and
	 * password. For Persistent Embedded (plocal) databases a 4th parameter can be passed to indicate if the DB schema
	 * should be created (e.g the DB is new). For In-Memory Embedded (memory) databases the schema is always created.
	 * For Persistent Remote (remote) databases the DB should exist and hence the schema can not be defined
	 */
	@Override
	public void configure(String[] configParameters) {
		
		url = configParameters[0];
		user = configParameters[1];
		password = configParameters[2];
		// "memory:EVLTrace"
		setup = Boolean.getBoolean(configParameters[3]);
		
	}

	@Override
	public void executionStarted() {
		
		if (url.startsWith("memory:")) {
			String name = url.split(":")[1];
			factory = OrientDbUtil.getInMemoryFactory(name);
		}
		// TODO Complete for other types
		
		orientDbDAO = new TraceOrientDbDAO(factory);
		if (url.startsWith("memory:")) {
			orientDbDAO.setupSchema();
		}
		
	}

	@Override
	public void executionFinished() {
		factory.close();
	}
	
	@Override
	public void incrementalExecutionStarted() {
		if (url.startsWith("memory:")) {
			String name = url.split(":")[1];
			factory = OrientDbUtil.getInMemoryFactory(name);
		}
		// TODO Complete for other types
	}
	


	@Override
	public void setExecutionContext(String scriptId, List<String> modelsIds) throws IncrementalExecutionException {
		ExecutionContextOrientDbImpl executionContext = (ExecutionContextOrientDbImpl) acquireExecutionContext(scriptId, modelsIds);
		executionContextId = executionContext.getId();
	}


	@Override
	public boolean createExecutionTraces(String moduleElementId, String elementId, List<String> properties) throws IncrementalExecutionException {
		for (String pName : properties) {
			createExecutionTrace(moduleElementId, elementId, pName);
		}
		return true;
	}

	@Override
	public boolean createExecutionTrace(String moduleElementId, String elementId, String propertyName) throws IncrementalExecutionException {
		
		ModuleElementOrientDbImpl moduleElement = acquireModuleElement(moduleElementId);
		ModelElementOrientDbImpl modelElement = acquireModelElement(elementId);
		TraceOrientDbImpl trace = acquireTrace(moduleElement, modelElement);
		PropertyOrientDbImpl property = findProperty(trace, modelElement, propertyName);
		if (property == null) {
			property = craeteProperty(propertyName);
			FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
			VTrace traceV = manager.frame(manager.getVertex(trace.getId()), VTrace.class);
			VProperty pV = manager.frame(manager.getVertex(property.getId()), VProperty.class);
			traceV.addAccesses(pV);
			VModelElement meV = manager.frame(manager.getVertex(modelElement.getId()), VModelElement.class);
			meV.addOwns(pV);
			try {
				trace = OrientDbUtil.wrap(TraceOrientDbImpl.class, traceV);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
			} finally {
				manager.commit();
				manager.shutdown();
			}
			return true;
		}
		return false;
	}
	
	
	private ExecutionContextOrientDbImpl acquireExecutionContext(String scriptId, List<String> modelsIds) throws IncrementalExecutionException {
		ExecutionContextOrientDbImpl ec = findExecutionContext(scriptId, modelsIds);
		if (ec == null) {
			ec = createExecutionContext(scriptId, modelsIds);
		}
		return ec;
	}

	private ExecutionContextOrientDbImpl createExecutionContext(String scriptId, List<String> modelsIds) throws IncrementalExecutionException {
		VExecutionContext ecV = orientDbDAO.createExecutionContext(scriptId, modelsIds);
		if (ecV == null) {
			throw new IncrementalExecutionException("Error creating the DB vertex in the DB.");
		}
		ExecutionContextOrientDbImpl ec = null;
		try {
			ec = OrientDbUtil.wrap(ExecutionContextOrientDbImpl.class, ecV);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance.", e);
		}
		return ec;
	}

	private PropertyOrientDbImpl craeteProperty(String propertyName) throws IncrementalExecutionException {
		VProperty proeprtyV = orientDbDAO.createProperty(propertyName);
		if (proeprtyV == null) {
			throw new IncrementalExecutionException("Error creating the DB vertex in the DB.");
		}
		PropertyOrientDbImpl property = null;
		try {
			property = OrientDbUtil.wrap(PropertyOrientDbImpl.class, proeprtyV);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		}
		return property;
	}

	private PropertyOrientDbImpl findProperty(final TraceOrientDbImpl trace, ModelElementOrientDbImpl modelElement, String propertyName) throws IncrementalExecutionException {
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		pipe.start(vertex.asVertex())
			.out(EContains.TRACE_TYPE)
			.filter(new PipeFunction<Vertex, Boolean>() {
				
				@Override
				public Boolean compute(Vertex v) {
					return v.getId().equals(trace.getId());		// Detach issue?
				}
			})
			.out(EAccesses.TRACE_TYPE)
			.has(VProperty.NAME, propertyName);
		PropertyOrientDbImpl result = null;
		try {
			result = OrientDbUtil.wrap(PropertyOrientDbImpl.class, manager.frame(pipe.next(), VProperty.class));
		} catch (NoSuchElementException e) {
			// Return null as indication of not finding any;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		} finally {
			manager.shutdown();
		}
		return result;
		
	}

	private TraceOrientDbImpl acquireTrace(ModuleElementOrientDbImpl moduleElement,
			ModelElementOrientDbImpl modelElement) throws IncrementalExecutionException {
		
		TraceOrientDbImpl trace = getTrace(moduleElement, modelElement);
		if (trace == null) {
			trace = createTrace();
			FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
			VTrace traceV = manager.frame(manager.getVertex(trace.getId()), VTrace.class);
			VModuleElement moduEV = manager.frame(manager.getVertex(moduleElement.getId()), VModuleElement.class);
			traceV.setTraces(moduEV);
			VModelElement modeEV = manager.frame(manager.getVertex(modelElement.getId()), VModelElement.class);
			traceV.addReaches(modeEV);
			VExecutionContext ecV = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
			ecV.addContains(traceV);
			try {
				trace = OrientDbUtil.wrap(TraceOrientDbImpl.class, traceV);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
			} finally {
				manager.commit();
				manager.shutdown();
			}
		}
		return trace;
	}

	private TraceOrientDbImpl createTrace() throws IncrementalExecutionException {
		VTrace traceV = orientDbDAO.createTrace();
		if (traceV == null) {
			throw new IncrementalExecutionException("Error creating the DB vertex in the DB.");
		}
		TraceOrientDbImpl trace = null;
		try {
			trace = OrientDbUtil.wrap(TraceOrientDbImpl.class, traceV);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		}
		return trace;
	}

	private TraceOrientDbImpl getTrace(final ModuleElementOrientDbImpl moduleElement, final ModelElementOrientDbImpl modelElement) throws IncrementalExecutionException {
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		pipe.start(vertex.asVertex())
			.out(EInvolves.TRACE_TYPE)
			.filter(new PipeFunction<Vertex, Boolean>() {

				@Override
				public Boolean compute(Vertex v) {
					return v.getId().equals(modelElement.getId());	// Will this fail because of detachment?
				}
			})
			.in(EReaches.TRACE_TYPE)
			.filter(new PipeFunction<Vertex, Boolean>() {
				
				@Override
				public Boolean compute(Vertex v) {
					return v.getId().equals(moduleElement.getId());
				}
			});
		TraceOrientDbImpl trace = null;
		try {
			trace = OrientDbUtil.wrap(TraceOrientDbImpl.class, manager.frame(pipe.next(), VTrace.class));
		} catch(NoSuchElementException e) {
			// Return null as indication of not finding any;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		} finally {
			manager.shutdown();
		}
		return trace;
		
	}

	private ModelElementOrientDbImpl acquireModelElement(String elementId) throws IncrementalExecutionException {
		ModelElementOrientDbImpl me = getModelElement(elementId);
		if (me == null) {
			me = createModelElement(elementId);
		}
		return me;
	}

	private ModelElementOrientDbImpl createModelElement(String elementId) throws IncrementalExecutionException {
		System.out.println("createModelElement " + elementId);
		VModelElement meV = orientDbDAO.createModelElement(elementId);
		
		if (meV == null) {
			throw new IncrementalExecutionException("Error creating the DB vertex in the DB.");
		}
		System.out.println("created for " + meV.getElement_id());
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		vertex.addInvolves(meV);
		manager.commit();
		manager.shutdown();
		ModelElementOrientDbImpl me = null;
		try {
			me = OrientDbUtil.wrap(ModelElementOrientDbImpl.class, meV);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		}
		return me;
	}

	private ModelElementOrientDbImpl getModelElement(String elementId) throws IncrementalExecutionException {
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		pipe.start(vertex.asVertex())
			.out(EInvolves.TRACE_TYPE)
			.has(VModelElement.ELEMENT_ID, elementId);
		ModelElementOrientDbImpl me = null;
		try {
			me = OrientDbUtil.wrap(ModelElementOrientDbImpl.class, manager.frame(pipe.next(), VModelElement.class));
		} catch(NoSuchElementException e) {
			// Return null as indication of not finding any;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		} finally {
			manager.shutdown();
		}
		return me;
	}

	private ModuleElementOrientDbImpl acquireModuleElement(String moduleElementId) throws IncrementalExecutionException {
		ModuleElementOrientDbImpl me = findModuleElement(moduleElementId);
		if (me == null) {
			me = createModuleElement(moduleElementId);
		}
		return me;
	}

	private ModuleElementOrientDbImpl createModuleElement(String moduleElementId) throws IncrementalExecutionException {
		VModuleElement meV = orientDbDAO.createModuleElement(moduleElementId);
		if (meV == null) {
			throw new IncrementalExecutionException("Error creating the DB vertex in the DB.");
		}
		// Add the module element to the context
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		vertex.addFor(meV);
		manager.commit();
		manager.shutdown();
		ModuleElementOrientDbImpl me = null;
		try {
			me = OrientDbUtil.wrap(ModuleElementOrientDbImpl.class, meV);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		}
		return me;
	}

	private ModuleElementOrientDbImpl findModuleElement(String moduleElementId) throws IncrementalExecutionException {
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		pipe.start(vertex.asVertex())
			.out(EFor.TRACE_TYPE)
			.has(VModuleElement.MODULE_ID, moduleElementId);
		ModuleElementOrientDbImpl me = null;
		try {
			me = OrientDbUtil.wrap(ModuleElementOrientDbImpl.class, manager.frame(pipe.next(), VModuleElement.class));
		} catch(NoSuchElementException e) {
			// Return null as indication of not finding any;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		} finally {
			manager.shutdown();
		}
		return me;
	}

	@Override
	public List<Trace> findExecutionTraces(String elementId, String propertyName)
			throws IncrementalExecutionException {
		
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		List<Trace> result = new ArrayList<>();
		pipe.start(vertex.asVertex())
			.out(EContains.TRACE_TYPE)
			.as("trace1")
			.out(EReaches.TRACE_TYPE)
			.has(VModelElement.ELEMENT_ID, elementId)
			.back("trace1")
			.as("trace2")
			.out(EAccesses.TRACE_TYPE)
			.has(VProperty.NAME, propertyName)
			.back("trace2");
		while (pipe.hasNext()) {
			Vertex v = pipe.next();
			try {
				result.add(OrientDbUtil.wrap(TraceOrientDbImpl.class, manager.frame(v, VTrace.class)));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
			} finally {
				manager.shutdown();
			}
		} 
		return result;
	}


	@Override
	public List<Trace> findExecutionTraces(String elementId) throws IncrementalExecutionException {
		final GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
		FramedTransactionalGraph<OrientGraph> manager = orientDbDAO.getManager();
		VExecutionContext vertex = manager.frame(manager.getVertex(executionContextId), VExecutionContext.class);
		pipe.start(vertex.asVertex())
			.out(EContains.TRACE_TYPE)
			.as("trace")
			.out(EReaches.TRACE_TYPE)
			.has(VModelElement.ELEMENT_ID, elementId)
			.back("trace");
		List<Trace> result = new ArrayList<>();
		for(Vertex v : pipe.toList()) {
			try {
				result.add(OrientDbUtil.wrap(TraceOrientDbImpl.class, manager.frame(v, VTrace.class)));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
			} finally {
				manager.shutdown();
			}
		}
		return result;
	}
	
	
	/**
	 * Gets an Execution Context vertex from the graph identified by the IDs of the script and models used during
	 * execution.
	 * 
	 * @param scriptId The script's ID
	 * @param modelsIds The models' IDs
	 * @return
	 * @throws IncrementalExecutionException 
	 */
	private ExecutionContextOrientDbImpl findExecutionContext(String scriptId, List<String> modelsIds) throws IncrementalExecutionException {
		List<VExecutionContext> vertices = orientDbDAO.getExecutionContextByIndex(scriptId);
		VExecutionContext ec_vertex = null;
		for (VExecutionContext v : vertices) {
			List<String> v_models = v.getModelsIds();
			v_models.removeAll(modelsIds);
			if (v_models.isEmpty()) {
				ec_vertex = v;
				break;
			}
			
		}
		try {
			return ec_vertex == null ? null : OrientDbUtil.wrap(ExecutionContextOrientDbImpl.class, ec_vertex);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new IncrementalExecutionException("Error creating the DB vertex wrapper instance..", e);
		}
	}

}

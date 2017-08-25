package org.eclipse.epsilon.orientdb.business.trace;

public class IncrementalExecutionException extends Exception {

	public IncrementalExecutionException(String string, Exception e) {
		super(string, e);
	}

	public IncrementalExecutionException(String string) {
		super(string);
	}

}

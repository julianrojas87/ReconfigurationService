package org.telcomp.utils;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

public class ExpressionEditor extends ExprEditor {
	
	int flag = 0;
	int inputsNumber;
	String candidateOpName;
	String parameterMapping;
	
	public ExpressionEditor(){}
	
	public ExpressionEditor(int f, String candidate, String mapping){
		this.inputsNumber = f;
		this.candidateOpName = candidate;
		this.parameterMapping = mapping;
	}
	
	public void edit(FieldAccess f) throws CannotCompileException{}

	public int getFlag() {
		return flag;
	}

	public void setFlag(int flag) {
		this.flag = flag;
	}

	public int getInputsNumber() {
		return inputsNumber;
	}

	public void setInputsNumber(int inputsNumber) {
		this.inputsNumber = inputsNumber;
	}

	public String getCandidateOpName() {
		return candidateOpName;
	}

	public void setCandidateOpName(String candidateOpName) {
		this.candidateOpName = candidateOpName;
	}

	public String getParameterMapping() {
		return parameterMapping;
	}

	public void setParameterMapping(String parameterMapping) {
		this.parameterMapping = parameterMapping;
	}
}

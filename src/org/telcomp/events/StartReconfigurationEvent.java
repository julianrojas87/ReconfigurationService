package org.telcomp.events;

import java.util.HashMap;
import java.util.Random;
import java.io.Serializable;

public final class StartReconfigurationEvent implements Serializable {

	private static final long serialVersionUID = 1L;
	private HashMap<String, Object> reconfigInputs = new HashMap<String, Object>();

	public StartReconfigurationEvent(HashMap<String, Object> hashMap) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		setReconfigInputs(hashMap);
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof StartReconfigurationEvent) && ((StartReconfigurationEvent)o).id == id;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String toString() {
		return "StartReconfigurationEvent[" + hashCode() + "]";
	}

	public HashMap<String, Object> getReconfigInputs() {
		return reconfigInputs;
	}

	public void setReconfigInputs(HashMap<String, Object> reconfigInputs) {
		this.reconfigInputs = reconfigInputs;
	}

	private final long id;
}

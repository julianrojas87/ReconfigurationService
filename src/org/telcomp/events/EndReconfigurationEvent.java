package org.telcomp.events;

import java.util.Random;
import java.io.Serializable;

public final class EndReconfigurationEvent implements Serializable {

	private static final long serialVersionUID = 1L;
	private boolean success;

	public EndReconfigurationEvent(boolean success) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		this.setSuccess(success);
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof EndReconfigurationEvent) && ((EndReconfigurationEvent)o).id == id;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String toString() {
		return "EndReconfigurationEvent[" + hashCode() + "]";
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	private final long id;
}

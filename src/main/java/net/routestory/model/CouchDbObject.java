package net.routestory.model;

import org.ektorp.CouchDbConnector;

public interface CouchDbObject {
	public void bind(CouchDbConnector couch);
}

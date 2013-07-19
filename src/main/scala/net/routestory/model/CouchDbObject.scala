package net.routestory.model

import org.ektorp.CouchDbConnector

trait CouchDbObject {
    def bind(couch: CouchDbConnector)
}

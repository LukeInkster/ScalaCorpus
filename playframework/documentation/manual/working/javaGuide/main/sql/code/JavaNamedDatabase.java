/*
 * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
 */
package javaguide.sql;

import javax.inject.Inject;

import play.mvc.Controller;
import play.db.NamedDatabase;
import play.db.Database;

// inject "orders" database instead of "default"
class JavaNamedDatabase extends Controller {
    private Database db;

    @Inject
    public JavaNamedDatabase(@NamedDatabase("orders") Database db) {
        this.db = db;
    }

    // do whatever you need with the db
}

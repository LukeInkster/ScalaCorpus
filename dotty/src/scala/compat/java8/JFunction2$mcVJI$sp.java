
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcVJI$sp extends JFunction2 {
    abstract void apply$mcVJI$sp(long v1, int v2);

    default Object apply(Object v1, Object v2) { apply$mcVJI$sp((Long) v1, (Integer) v2); return scala.runtime.BoxedUnit.UNIT; }
}

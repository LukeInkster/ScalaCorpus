
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcVDJ$sp extends JFunction2 {
    abstract void apply$mcVDJ$sp(double v1, long v2);

    default Object apply(Object v1, Object v2) { apply$mcVDJ$sp((Double) v1, (Long) v2); return scala.runtime.BoxedUnit.UNIT; }
}

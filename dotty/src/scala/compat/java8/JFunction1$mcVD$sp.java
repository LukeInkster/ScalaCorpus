
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcVD$sp extends JFunction1 {
    abstract void apply$mcVD$sp(double v1);

    default Object apply(Object t) { apply$mcVD$sp((Double) t); return scala.runtime.BoxedUnit.UNIT; }
}

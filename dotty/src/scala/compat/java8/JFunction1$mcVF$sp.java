
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcVF$sp extends JFunction1 {
    abstract void apply$mcVF$sp(float v1);

    default Object apply(Object t) { apply$mcVF$sp((Float) t); return scala.runtime.BoxedUnit.UNIT; }
}

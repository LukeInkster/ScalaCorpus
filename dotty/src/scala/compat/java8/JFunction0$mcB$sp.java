
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction0$mcB$sp extends JFunction0 {
    abstract byte apply$mcB$sp();

    default Object apply() { return (Byte) apply$mcB$sp(); }
}

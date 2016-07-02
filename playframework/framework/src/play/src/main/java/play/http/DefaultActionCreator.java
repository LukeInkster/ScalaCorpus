/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.http;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;

/**
 * A default implementation of the action creator.
 *
 * To create a custom action creator, extend this class or implement the ActionCreator interface directly.
 */
public class DefaultActionCreator implements ActionCreator {

  @Override
  public Action createAction(Request request, Method actionMethod) {
    return new Action.Simple() {
      @Override
      public CompletionStage<Result> call(Http.Context ctx) {
        return delegate.call(ctx);
      }
    };
  }

  @Override
  public final Action wrapAction(Action action) {
    return action;
  }
}

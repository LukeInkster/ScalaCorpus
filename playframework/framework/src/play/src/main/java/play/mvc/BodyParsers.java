/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.mvc;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import play.api.http.Status$;
import play.http.HttpErrorHandler;
import play.libs.F;
import play.libs.streams.Accumulator;
import play.core.j.JavaParsers;

import akka.util.ByteString;
import akka.stream.Materializer;

/**
 * Utilities for creating body parsers.
 */
public class BodyParsers {

    /**
     * Validate the content type of the passed in request using the given validator.
     *
     * If the validator returns true, the passed in accumulator will be returned to parse the body, otherwise an
     * accumulator with a result created by the error handler will be returned.
     *
     * @param errorHandler The error handler used to create a bad request result if the content type is not valid.
     * @param request The request to validate.
     * @param errorMessage The error message to pass to the error handler if the content type is not valid.
     * @param validate The validation function.
     * @param parser The parser to use if the content type is valid.
     * @param <A> The type to be parsed by the parser
     * @return An accumulator to parse the body.
     */
    public static <A> Accumulator<ByteString, F.Either<Result, A>> validateContentType(HttpErrorHandler errorHandler,
               Http.RequestHeader request, String errorMessage, Function<String, Boolean> validate,
               Function<Http.RequestHeader, Accumulator<ByteString, F.Either<Result, A>>> parser) {
        if (request.contentType().map(validate).orElse(false)) {
            return parser.apply(request);
        } else {
            CompletionStage<Result> result =
                    errorHandler.onClientError(request, Status$.MODULE$.UNSUPPORTED_MEDIA_TYPE(), errorMessage);
            return Accumulator.done(result.thenApply(F.Either::Left));
        }
    }

    static <A, B> Accumulator<ByteString, F.Either<Result, A>> delegate(play.api.mvc.BodyParser<B> delegate, Function<B, A> transform, Http.RequestHeader request) {
        Accumulator<ByteString, scala.util.Either<play.api.mvc.Result, B>> javaAccumulator = delegate.apply(request._underlyingHeader()).asJava();
            
        return javaAccumulator.map(result -> {
                if (result.isLeft()) {
                    return F.Either.Left(result.left().get().asJava());
                } else {
                    return F.Either.Right(transform.apply(result.right().get()));
                }
            },
            JavaParsers.trampoline());
    }
}

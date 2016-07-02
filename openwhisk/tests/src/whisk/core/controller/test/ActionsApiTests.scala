/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.controller.test

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import spray.http.StatusCodes.Accepted
import spray.http.StatusCodes.BadRequest
import spray.http.StatusCodes.Conflict
import spray.http.StatusCodes.Forbidden
import spray.http.StatusCodes.InternalServerError
import spray.http.StatusCodes.MethodNotAllowed
import spray.http.StatusCodes.NotFound
import spray.http.StatusCodes.OK
import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import spray.json.DefaultJsonProtocol.RootJsObjectFormat
import spray.json.DefaultJsonProtocol.listFormat
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.DefaultJsonProtocol.vectorFormat
import spray.json.DefaultJsonProtocol.mapFormat
import spray.json.JsObject
import spray.json.pimpAny
import spray.json.pimpString
import whisk.common.Verbosity
import whisk.core.controller.WhiskActionsApi
import whisk.core.entity.ActionLimits
import whisk.core.entity.ActionLimitsOption
import whisk.core.entity.ActivationResponse
import whisk.core.entity.ActivationLogs
import whisk.core.entity.AuthKey
import whisk.core.entity.Exec
import whisk.core.entity.MemoryLimit
import whisk.core.entity.Namespace
import whisk.core.entity.Parameters
import whisk.core.entity.SemVer
import whisk.core.entity.Subject
import whisk.core.entity.TimeLimit
import whisk.core.entity.WhiskAction
import whisk.core.entity.WhiskActionPut
import whisk.core.entity.WhiskActivation
import whisk.core.entity.WhiskAuth
import whisk.core.entity.WhiskEntity
import java.time.Instant
import whisk.core.entity.SequenceExec
import whisk.core.entity.Pipecode
import whisk.core.entity.NodeJSExec

/**
 * Tests Actions API.
 *
 * Unit tests of the controller service as a standalone component.
 * These tests exercise a fresh instance of the service object in memory -- these
 * tests do NOT communication with a whisk deployment.
 *
 *
 * @Idioglossia
 * "using Specification DSL to write unit tests, as in should, must, not, be"
 * "using Specs2RouteTest DSL to chain HTTP requests for unit testing, as in ~>"
 */
@RunWith(classOf[JUnitRunner])
class ActionsApiTests extends ControllerTestCommon with WhiskActionsApi {

    /** Actions API tests */
    behavior of "Actions API"

    val creds = WhiskAuth(Subject(), AuthKey())
    val namespace = Namespace(creds.subject())
    val collectionPath = s"/${Namespace.DEFAULT}/${collection.path}"
    def aname = MakeName.next("action_tests")
    setVerbosity(Verbosity.Loud)

    //// GET /actions
    it should "list actions by default namespace" in {
        implicit val tid = transid()
        val actions = (1 to 2).map { i =>
            WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        }.toList
        actions foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskAction, namespace, 2)
        Get(s"$collectionPath") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            actions.length should be(response.length)
            actions forall { a => response contains a.summaryAsJson } should be(true)
        }
    }

    // ?docs disabled
    ignore should "list action by default namespace with full docs" in {
        implicit val tid = transid()
        val actions = (1 to 2).map { i =>
            WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        }.toList
        actions foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskAction, namespace, 2)
        Get(s"$collectionPath?docs=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[WhiskAction]]
            actions.length should be(response.length)
            actions forall { a => response contains a } should be(true)
        }
    }

    it should "list action with explicit namespace" in {
        implicit val tid = transid()
        val actions = (1 to 2).map { i =>
            WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        }.toList
        actions foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskAction, namespace, 2)
        Get(s"/$namespace/${collection.path}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            actions.length should be(response.length)
            actions forall { a => response contains a.summaryAsJson } should be(true)
        }

        // it should "reject list action with explicit namespace not owned by subject" in {
        val auser = WhiskAuth(Subject(), AuthKey())
        Get(s"/$namespace/${collection.path}") ~> sealRoute(routes(auser)) ~> check {
            status should be(Forbidden)
        }
    }

    it should "list should reject request with post" in {
        implicit val tid = transid()
        Post(s"$collectionPath") ~> sealRoute(routes(creds)) ~> check {
            status should be(MethodNotAllowed)
        }
    }

    //// GET /actions/name
    it should "get action by name in default namespace" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        put(entityStore, action)
        Get(s"$collectionPath/${action.name}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }
    }

    it should "get action by name in explicit namespace" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        put(entityStore, action)
        Get(s"/$namespace/${collection.path}/${action.name}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }

        // it should "reject get action by name in explicit namespace not owned by subject" in
        val auser = WhiskAuth(Subject(), AuthKey())
        Get(s"/$namespace/${collection.path}/${action.name}") ~> sealRoute(routes(auser)) ~> check {
            status should be(Forbidden)
        }
    }

    it should "report NotFound for get non existent action" in {
        implicit val tid = transid()
        Get(s"$collectionPath/xyz") ~> sealRoute(routes(creds)) ~> check {
            status should be(NotFound)
        }
    }

    //// DEL /actions/name
    it should "delete action by name" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        put(entityStore, action)

        // it should "reject delete action by name not owned by subject" in
        val auser = WhiskAuth(Subject(), AuthKey())
        Get(s"/$namespace/${collection.path}/${action.name}") ~> sealRoute(routes(auser)) ~> check {
            status should be(Forbidden)
        }

        Delete(s"$collectionPath/${action.name}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }
    }

    it should "report NotFound for delete non existent action" in {
        implicit val tid = transid()
        Delete(s"$collectionPath/xyz") ~> sealRoute(routes(creds)) ~> check {
            status should be(NotFound)
        }
    }

    //// PUT /actions/name
    it should "put should reject request missing json content" in {
        implicit val tid = transid()
        Put(s"$collectionPath/xxx", "") ~> sealRoute(routes(creds)) ~> check {
            val response = responseAs[String]
            status should be(BadRequest)
        }
    }

    it should "put should reject request missing property exec" in {
        implicit val tid = transid()
        val content = """|{"name":"name","publish":true}""".stripMargin.parseJson.asJsObject
        Put(s"$collectionPath/xxx", content) ~> sealRoute(routes(creds)) ~> check {
            val response = responseAs[String]
            status should be(BadRequest)
        }
    }

    it should "put should reject request with malformed property exec" in {
        implicit val tid = transid()
        val content = """|{"name":"name",
                         |"publish":true,
                         |"exec":""}""".stripMargin.parseJson.asJsObject
        Put(s"$collectionPath/xxx", content) ~> sealRoute(routes(creds)) ~> check {
            val response = responseAs[String]
            status should be(BadRequest)
        }
    }

    it should "put should accept request with missing optional properties" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"))
        val content = WhiskActionPut(Some(action.exec))
        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }
    }

    private def seqParameters(seq: Vector[String]) = Parameters("_actions", seq.toJson)

    it should "create an action sequence" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence))
        val content = WhiskActionPut(Some(action.exec))

        // create an action sequence
        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec shouldBe a[SequenceExec]
            response.exec.kind should be(Exec.SEQUENCE)
            val seq = response.exec.asInstanceOf[SequenceExec]
            seq.code should be(Pipecode.code)
            seq.components should be(sequence)
            response.parameters shouldBe seqParameters(sequence)
        }
    }

    it should "create an action sequence ignoring parameters" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence))
        val content = WhiskActionPut(Some(action.exec), parameters = Some(Parameters("x", "X")))

        // create an action sequence
        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec shouldBe a[SequenceExec]
            response.exec.kind should be(Exec.SEQUENCE)
            val seq = response.exec.asInstanceOf[SequenceExec]
            seq.code should be(Pipecode.code)
            seq.components should be(sequence)
            response.parameters shouldBe seqParameters(sequence)
        }
    }

    it should "update an action sequence with a new sequence" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val newSequence = Vector("c", "d")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence), seqParameters(sequence))
        val content = WhiskActionPut(Some(Exec.sequence(newSequence)))
        put(entityStore, action, false)

        // create an action sequence
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec shouldBe a[SequenceExec]
            response.exec.kind should be(Exec.SEQUENCE)
            val seq = response.exec.asInstanceOf[SequenceExec]
            seq.code should be(Pipecode.code)
            seq.components should be(newSequence)
            response.parameters shouldBe seqParameters(newSequence)
        }
    }

    it should "update an action sequence ignoring parameters" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence), seqParameters(sequence))
        val content = WhiskActionPut(parameters = Some(Parameters("a", "A")))
        put(entityStore, action, false)

        // create an action sequence
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec shouldBe a[SequenceExec]
            response.exec.kind should be(Exec.SEQUENCE)
            val seq = response.exec.asInstanceOf[SequenceExec]
            seq.code should be(Pipecode.code)
            seq.components should be(sequence)
            response.parameters shouldBe seqParameters(sequence)
        }
    }

    it should "reset parameters when changing sequence action to non sequence" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence), seqParameters(sequence))
        val content = WhiskActionPut(Some(Exec.js("")))
        put(entityStore, action, false)

        // create an action sequence
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec.kind should be(Exec.NODEJS)
            response.parameters shouldBe Parameters()
        }
    }

    it should "preserve new parameters when changing sequence action to non sequence" in {
        implicit val tid = transid()
        val sequence = Vector("a", "b")
        val action = WhiskAction(namespace, aname, Exec.sequence(sequence), seqParameters(sequence))
        val content = WhiskActionPut(Some(Exec.js("")), parameters = Some(Parameters("a", "A")))
        put(entityStore, action, false)

        // create an action sequence
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response.exec.kind should be(Exec.NODEJS)
            response.parameters should be(Parameters("a", "A"))
        }
    }

    it should "put should accept request with parameters property" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(action.exec), Some(action.parameters))

        // it should "reject put action in namespace not owned by subject" in
        val auser = WhiskAuth(Subject(), AuthKey())
        Put(s"/$namespace/${collection.path}/${action.name}", content) ~> sealRoute(routes(auser)) ~> check {
            status should be(Forbidden)
        }

        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }
    }

    it should "put should reject request with parameters property as jsobject" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(action.exec), Some(action.parameters))
        val params = """{ "parameters": { "a": "b" } }""".parseJson.asJsObject
        val json = JsObject(WhiskActionPut.serdes.write(content).asJsObject.fields ++ params.fields)
        Put(s"$collectionPath/${action.name}", json) ~> sealRoute(routes(creds)) ~> check {
            status should be(BadRequest)
        }
    }

    it should "put should accept request with limits property" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(action.exec), Some(action.parameters), Some(ActionLimitsOption(Some(action.limits.timeout), Some(action.limits.memory))))
        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be(action)
        }
    }

    it should "put and then get action from cache" in {
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(action.exec), Some(action.parameters), Some(ActionLimitsOption(Some(action.limits.timeout), Some(action.limits.memory))))
        val name = action.name

        val stream = new ByteArrayOutputStream
        val printstream = new PrintStream(stream)
        val savedstream = authStore.outputStream
        entityStore.outputStream = printstream
        try {
            // first request invalidates any previous entries and caches new result
            Put(s"$collectionPath/$name", content) ~> sealRoute(routes(creds)(transid())) ~> check {
                status should be(OK)
                val response = responseAs[WhiskAction]
                response should be(action)
            }
            stream.toString should include regex (s"caching*.*${action.docid.asDocInfo}")
            stream.reset()

            // second request should fetch from cache
            Get(s"$collectionPath/$name") ~> sealRoute(routes(creds)(transid())) ~> check {
                status should be(OK)
                val response = responseAs[WhiskAction]
                response should be(action)
            }

            stream.toString should include regex (s"serving from cache:*.*${action.docid.asDocInfo}")
            stream.reset()

            // delete should invalidate cache
            Delete(s"$collectionPath/$name") ~> sealRoute(routes(creds)(transid())) ~> check {
                status should be(OK)
                val response = responseAs[WhiskAction]
                response should be(action)
            }
            stream.toString should include regex (s"invalidating*.*${action.docid.asDocInfo}")
            stream.reset()
        } finally {
            entityStore.outputStream = savedstream
            stream.close()
            printstream.close()
        }
    }

    it should "reject put with conflict for pre-existing action" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(action.exec))
        put(entityStore, action)
        Put(s"$collectionPath/${action.name}", content) ~> sealRoute(routes(creds)) ~> check {
            status should be(Conflict)
        }
    }

    it should "update action with a put" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(Some(Exec.js("_")), Some(Parameters("x", "X")))
        put(entityStore, action)
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be {
                WhiskAction(action.namespace, action.name, content.exec.get, content.parameters.get, version = action.version.upPatch)
            }
        }
    }

    it should "update action parameters with a put" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val content = WhiskActionPut(parameters = Some(Parameters("x", "X")))
        put(entityStore, action)
        Put(s"$collectionPath/${action.name}?overwrite=true", content) ~> sealRoute(routes(creds)) ~> check {
            deleteAction(action.docid)
            status should be(OK)
            val response = responseAs[WhiskAction]
            response should be {
                WhiskAction(action.namespace, action.name, action.exec, content.parameters.get, version = action.version.upPatch)
            }
        }
    }

    //// POST /actions/name
    it should "invoke an action with arguments, nonblocking" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), Parameters("x", "b"))
        val args = JsObject("xxx" -> "yyy".toJson)
        put(entityStore, action)

        // it should "reject post to action in namespace not owned by subject"
        val auser = WhiskAuth(Subject(), AuthKey())
        Post(s"/$namespace/${collection.path}/${action.name}", args) ~> sealRoute(routes(auser)) ~> check {
            status should be(Forbidden)
        }

        Post(s"$collectionPath/${action.name}", args) ~> sealRoute(routes(creds)) ~> check {
            status should be(Accepted)
            val response = responseAs[JsObject]
            response.fields("activationId") should not be None
        }

        // it should "ignore &result when invoking nonblocking action"
        Post(s"$collectionPath/${action.name}?result=true", args) ~> sealRoute(routes(creds)) ~> check {
            status should be(Accepted)
            val response = responseAs[JsObject]
            response.fields("activationId") should not be None
        }
    }

    it should "invoke an action, nonblocking" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"))
        put(entityStore, action)
        Post(s"$collectionPath/${action.name}") ~> sealRoute(routes(creds)) ~> check {
            status should be(Accepted)
            val response = responseAs[JsObject]
            response.fields("activationId") should not be None
        }
    }

    it should "invoke an action, blocking with timeout" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"), limits = ActionLimits(TimeLimit(1000), MemoryLimit()))
        put(entityStore, action)
        Post(s"$collectionPath/${action.name}?blocking=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(Accepted)
            val response = responseAs[JsObject]
            response.fields("activationId") should not be None
        }
    }

    it should "invoke an action, blocking" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"))
        val activation = WhiskActivation(action.namespace, action.name, creds.subject, activationId,
            start = Instant.now,
            end = Instant.now,
            response = ActivationResponse.success(Some(JsObject("test" -> "yes".toJson))))
        put(entityStore, action)
        put(activationStore, activation)
        Post(s"$collectionPath/${action.name}?blocking=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[JsObject]
            response should be(activation.toExtendedJson)
        }

        // repeat invoke, get only result back
        Post(s"$collectionPath/${action.name}?blocking=true&result=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[JsObject]
            response should be(activation.resultAsJson)
        }

        deleteActivation(activation.docid)
    }

    it should "invoke a blocking action and return error response when activation fails" in {
        implicit val tid = transid()
        val action = WhiskAction(namespace, aname, Exec.js("??"))
        val activation = WhiskActivation(action.namespace, action.name, creds.subject, activationId,
            start = Instant.now,
            end = Instant.now,
            response = ActivationResponse.whiskError("test"))
        put(entityStore, action)
        put(activationStore, activation)
        Post(s"$collectionPath/${action.name}?blocking=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(InternalServerError)
            val response = responseAs[JsObject]
            response should be(activation.toExtendedJson)
        }

        deleteActivation(activation.docid)
    }
}

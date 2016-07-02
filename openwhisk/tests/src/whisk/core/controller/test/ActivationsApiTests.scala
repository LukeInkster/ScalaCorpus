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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import spray.http.StatusCodes.BadRequest
import spray.http.StatusCodes.NotFound
import spray.http.StatusCodes.MethodNotAllowed
import spray.http.StatusCodes.OK
import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import spray.json.DefaultJsonProtocol.RootJsObjectFormat
import spray.json.DefaultJsonProtocol.listFormat
import spray.json.JsObject
import spray.json.pimpString
import whisk.core.controller.WhiskActivationsApi
import whisk.core.entity.ActivationId
import whisk.core.entity.EntityName
import whisk.core.entity.Namespace
import whisk.core.entity.SemVer
import whisk.core.entity.AuthKey
import whisk.core.entity.WhiskAuth
import whisk.core.entity.Subject
import whisk.core.entity.WhiskActivation
import whisk.core.entity.WhiskEntity
import spray.json.JsString
import whisk.core.entity.ActivationResponse
import whisk.core.entity.ActivationLogs
import java.time.Instant
import whisk.core.entity.ActionLimits
import whisk.core.entity.Exec
import whisk.core.entity.WhiskAction
import java.time.Clock
import spray.json.JsNumber

/**
 * UNDER CONSTRUCTION: tests for new scala controller
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
class ActivationsApiTests extends ControllerTestCommon with WhiskActivationsApi {

    /** Activations API tests */
    behavior of "Activations API"

    val creds = WhiskAuth(Subject(), AuthKey())
    val namespace = Namespace(creds.subject())
    val collectionPath = s"/${Namespace.DEFAULT}/${collection.path}"
    def aname = MakeName.next("activations_tests")

    //// GET /activations
    it should "get summary activation by namespace" in {
        implicit val tid = transid()
        // create two sets of activation records, and check that only one set is served back
        val creds1 = WhiskAuth(Subject(), AuthKey())
        (1 to 2).map { i =>
            WhiskActivation(Namespace(creds1.subject()), aname, creds1.subject, ActivationId(), start = Instant.now, end = Instant.now)
        } foreach { put(entityStore, _) }

        val actionName = aname
        val activations = (1 to 2).map { i =>
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        }.toList
        activations foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskActivation, namespace, 2)

        Get(s"$collectionPath") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val rawResponse = responseAs[List[JsObject]]
            val response = responseAs[List[JsObject]]
            activations.length should be(response.length)
            activations forall { a => response contains a.summaryAsJson } should be(true)
            rawResponse forall { a => a.getFields("for") match { case Seq(JsString(n)) => n == actionName() case _ => false } }
        }
    }

    //// GET /activations?docs=true
    it should "get full activation by namespace" in {
        implicit val tid = transid()
        // create two sets of activation records, and check that only one set is served back
        val creds1 = WhiskAuth(Subject(), AuthKey())
        (1 to 2).map { i =>
            WhiskActivation(Namespace(creds1.subject()), aname, creds1.subject, ActivationId(), start = Instant.now, end = Instant.now)
        } foreach { put(entityStore, _) }

        val actionName = aname
        val activations = (1 to 2).map { i =>
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = Instant.now, end = Instant.now, response = ActivationResponse.success(Some(JsNumber(5))))
        }.toList
        activations foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskActivation, namespace, 2)

        Get(s"$collectionPath?docs=true") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            activations.length should be(response.length)
            activations forall { a => response contains a.toExtendedJson } should be(true)
        }
    }

    //// GET /activations?docs=true&since=xxx&upto=yyy
    it should "get full activation by namespace within a date range" in {
        implicit val tid = transid()
        // create two sets of activation records, and check that only one set is served back
        val creds1 = WhiskAuth(Subject(), AuthKey())
        (1 to 2).map { i =>
            WhiskActivation(Namespace(creds1.subject()), aname, creds1.subject, ActivationId(), start = Instant.now, end = Instant.now)
        } foreach { put(entityStore, _) }

        val actionName = aname
        val now = Instant.now(Clock.systemUTC())
        val since = now.plusSeconds(10)
        val upto = now.plusSeconds(30)
        implicit val activations = Seq(
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = now.plusSeconds(9), end = now),
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = now.plusSeconds(20), end = now.plusSeconds(20)), // should match
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = now.plusSeconds(10), end = now.plusSeconds(20)), // should match
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = now.plusSeconds(31), end = now.plusSeconds(20)),
            WhiskActivation(namespace, actionName, creds.subject, ActivationId(), start = now.plusSeconds(30), end = now.plusSeconds(20))) // should match
        activations foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskActivation, namespace, activations.length)

        // get between two time stamps
        Get(s"$collectionPath?docs=true&since=${since.toEpochMilli}&upto=${upto.toEpochMilli}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            val expected = activations filter {
                case e => (e.start.equals(since) || e.start.equals(upto) || (e.start.isAfter(since) && e.start.isBefore(upto)))
            }
            expected.length should be(response.length)
            expected forall { a => response contains a.toExtendedJson } should be(true)
        }

        // get 'upto' with no defined since value should return all activation 'upto'
        Get(s"$collectionPath?docs=true&upto=${upto.toEpochMilli}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            val expected = activations filter {
                case e => e.start.equals(upto) || e.start.isBefore(upto)
            }
            expected.length should be(response.length)
            expected forall { a => response contains a.toExtendedJson } should be(true)
        }

        // get 'since' with no defined upto value should return all activation 'since'
        Get(s"$collectionPath?docs=true&since=${since.toEpochMilli}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            val expected = activations filter {
                case e => e.start.equals(since) || e.start.isAfter(since)
            }
            expected.length should be(response.length)
            expected forall { a => response contains a.toExtendedJson } should be(true)
        }
    }

    //// GET /activations?name=xyz
    it should "get summary activation by namespace and action name" in {
        implicit val tid = transid()

        // create two sets of activation records, and check that only one set is served back
        val creds1 = WhiskAuth(Subject(), AuthKey())
        (1 to 2).map { i =>
            WhiskActivation(Namespace(creds1.subject()), aname, creds1.subject, ActivationId(), start = Instant.now, end = Instant.now)
        } foreach { put(entityStore, _) }

        val activations = (1 to 2).map { i =>
            WhiskActivation(namespace, EntityName(s"xyz"), creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        }.toList
        activations foreach { put(entityStore, _) }
        waitOnView(entityStore, WhiskActivation, namespace, 2)

        Get(s"$collectionPath?name=xyz") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[List[JsObject]]
            activations.length should be(response.length)
            activations forall { a => response contains a.summaryAsJson } should be(true)
        }
    }

    it should "reject get activation by namespace and action name when action name is not a valid name" in {
        implicit val tid = transid()
        Get(s"$collectionPath?name=0%20") ~> sealRoute(routes(creds)) ~> check {
            status should be(BadRequest)
        }
    }

    it should "reject get activation with invalid since/upto value" in {
        implicit val tid = transid()
        Get(s"$collectionPath?since=xxx") ~> sealRoute(routes(creds)) ~> check {
            status should be(BadRequest)
        }
        Get(s"$collectionPath?upto=yyy") ~> sealRoute(routes(creds)) ~> check {
            status should be(BadRequest)
        }
    }

    //// GET /activations/id
    it should "get activation by id" in {
        implicit val tid = transid()
        val activation = WhiskActivation(namespace, aname, creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        put(entityStore, activation)

        Get(s"$collectionPath/${activation.activationId()}") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[JsObject]
            response should be(activation.toExtendedJson)
        }
    }

    //// GET /activations/id/result
    it should "get activation result by id" in {
        implicit val tid = transid()
        val activation = WhiskActivation(namespace, aname, creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        put(entityStore, activation)

        Get(s"$collectionPath/${activation.activationId()}/result") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[JsObject]
            response should be(activation.response.toExtendedJson)
        }
    }

    //// GET /activations/id/logs
    it should "get activation logs by id" in {
        implicit val tid = transid()
        val activation = WhiskActivation(namespace, aname, creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        put(entityStore, activation)

        Get(s"$collectionPath/${activation.activationId()}/logs") ~> sealRoute(routes(creds)) ~> check {
            status should be(OK)
            val response = responseAs[JsObject]
            response should be(activation.logs.toJsonObject)
        }
    }

    //// GET /activations/id/bogus
    it should "reject request to invalid activation resource" in {
        implicit val tid = transid()
        val activation = WhiskActivation(namespace, aname, creds.subject, ActivationId(), start = Instant.now, end = Instant.now)
        put(entityStore, activation)

        Get(s"$collectionPath/${activation.activationId()}/bogus") ~> sealRoute(routes(creds)) ~> check {
            status should be(NotFound)
        }
    }

    it should "reject request with put" in {
        implicit val tid = transid()
        Put(s"$collectionPath/${ActivationId()}") ~> sealRoute(routes(creds)) ~> check {
            status should be(MethodNotAllowed)
        }
    }

    it should "reject request with post" in {
        implicit val tid = transid()
        Post(s"$collectionPath/${ActivationId()}") ~> sealRoute(routes(creds)) ~> check {
            status should be(MethodNotAllowed)
        }
    }

    it should "reject request with delete" in {
        implicit val tid = transid()
        Delete(s"$collectionPath/${ActivationId()}") ~> sealRoute(routes(creds)) ~> check {
            status should be(MethodNotAllowed)
        }
    }
}

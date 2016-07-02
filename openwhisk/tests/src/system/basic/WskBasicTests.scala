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

package system.basic

import java.io.File
import java.time.Instant

import org.apache.commons.io.FileUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import common.TestHelpers
import common.TestUtils
import common.TestUtils.ANY_ERROR_EXIT
import common.TestUtils.BAD_REQUEST
import common.TestUtils.CONFLICT
import common.TestUtils.FORBIDDEN
import common.TestUtils.MISUSE_EXIT
import common.TestUtils.NOTALLOWED
import common.TestUtils.NOT_FOUND
import common.TestUtils.SUCCESS_EXIT
import common.TestUtils.TIMEOUT
import common.TestUtils.UNAUTHORIZED
import common.TestUtils.ERROR_EXIT
import common.Wsk
import common.WskProps
import common.WskTestHelpers
import spray.json.DefaultJsonProtocol.JsValueFormat
import spray.json.DefaultJsonProtocol.LongJsonFormat
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.DefaultJsonProtocol.mapFormat
import spray.json.pimpAny
import whisk.core.entity.WhiskPackage

@RunWith(classOf[JUnitRunner])
class WskBasicTests
    extends TestHelpers
    with WskTestHelpers {

    implicit val wskprops = WskProps()
    var usePythonCLI = true
    val wsk = new Wsk(usePythonCLI)
    val defaultAction = Some(TestUtils.getCatalogFilename("samples/hello.js"))

    behavior of "Wsk CLI"

    it should "confirm wsk exists" in {
        Wsk.exists(wsk.usePythonCLI)
    }

    it should "show help and usage info" in {
        val stdout = wsk.cli(Seq("-h")).stdout

        if (usePythonCLI) {
            stdout should include("usage:")
            stdout should include("optional arguments")
            stdout should include("available commands")
            stdout should include("-help")
        } else {
            stdout should include regex ("""(?i)Usage:""")
            stdout should include regex ("""(?i)Flags""")
            stdout should include regex ("""(?i)Available commands""")
            stdout should include regex ("""(?i)--help""")
        }
    }

    it should "show cli build version" in {
        val stdout = wsk.cli(Seq("property", "get", "--cliversion")).stdout
        stdout should include regex ("""(?i)whisk CLI version\s+201.*""")
    }

    it should "show api version" in {
        val stdout = wsk.cli(Seq("property", "get", "--apiversion")).stdout
        stdout should include regex ("""(?i)whisk API version\s+v1""")
    }

    it should "show api build version" in {
        val stdout = wsk.cli(wskprops.overrides ++ Seq("property", "get", "--apibuild")).stdout
        stdout should include regex ("""(?i)whisk API build\s+201.*""")
    }

    it should "show api build number" in {
        val stdout = wsk.cli(wskprops.overrides ++ Seq("property", "get", "--apibuildno")).stdout
        stdout should include regex ("""(?i)whisk API build.*\s+.*""")
    }

    it should "set auth in property file" in {
        val wskprops = File.createTempFile("wskprops", ".tmp")
        val env = Map("WSK_CONFIG_FILE" -> wskprops.getAbsolutePath())
        wsk.cli(Seq("property", "set", "--auth", "testKey"), env = env)
        val fileContent = FileUtils.readFileToString(wskprops)
        fileContent should include("AUTH=testKey")
        wskprops.delete()
    }

    it should "reject creating duplicate entity" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testDuplicateCreate"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, name, confirmDelete = false) {
                (action, _) => action.create(name, defaultAction, expectedExitCode = CONFLICT)
            }
    }

    it should "reject deleting entity in wrong collection" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testCrossDelete"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }
            wsk.action.delete(name, expectedExitCode = CONFLICT)
    }

    it should "reject creating entities with invalid names" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val names = Seq(
                ("", NOTALLOWED),
                (" ", BAD_REQUEST),
                ("hi+there", BAD_REQUEST),
                ("$hola", BAD_REQUEST),
                ("dora?", BAD_REQUEST),
                ("|dora|dora?", BAD_REQUEST))

            names foreach {
                case (name, ec) =>
                    assetHelper.withCleaner(wsk.action, name, confirmDelete = false) {
                        (action, _) => action.create(name, defaultAction, expectedExitCode = ec)
                    }
            }
    }

    it should "reject unauthenticated access" in {
        implicit val wskprops = WskProps("xxx") // shadow properties
        val errormsg = "The supplied authentication is invalid"
        wsk.namespace.list(expectedExitCode = UNAUTHORIZED).
            stderr should include(errormsg)
        wsk.namespace.get(expectedExitCode = UNAUTHORIZED).
            stderr should include(errormsg)
    }

    it should "reject deleting action in shared package not owned by authkey" in {
        wsk.action.get("/whisk.system/util/cat") // make sure it exists
        wsk.action.delete("/whisk.system/util/cat", expectedExitCode = FORBIDDEN)
    }

    it should "reject create action in shared package not owned by authkey" in {
        wsk.action.get("/whisk.system/util/notallowed", expectedExitCode = NOT_FOUND) // make sure it does not exist
        val file = Some(TestUtils.getCatalogFilename("samples/hello.js"))
        try {
            wsk.action.create("/whisk.system/util/notallowed", file, expectedExitCode = FORBIDDEN)
        } finally {
            wsk.action.sanitize("/whisk.system/util/notallowed")
        }
    }

    it should "reject update action in shared package not owned by authkey" in {
        wsk.action.create("/whisk.system/util/cat", None,
            update = true, shared = Some(true), expectedExitCode = FORBIDDEN)
    }

    it should "reject bad command" in {
        if (usePythonCLI) {
            wsk.cli(Seq("bogus"), expectedExitCode = MISUSE_EXIT).
            stderr should include("usage:")
        } else {
            val result = wsk.cli(Seq("bogus"), expectedExitCode = ERROR_EXIT)
            result.stderr should include regex ("""(?i)Run 'wsk --help' for usage""")
        }
    }

    it should "reject authenticated command when no auth key is given" in {
        // override wsk props file in case it exists
        val wskprops = File.createTempFile("wskprops", ".tmp")
        val env = Map("WSK_CONFIG_FILE" -> wskprops.getAbsolutePath())

        if (usePythonCLI) {
            val stderr = wsk.cli(Seq("list"), env = env, expectedExitCode = MISUSE_EXIT).stderr
            stderr should include("usage:")
            stderr should include("--auth is required")
        } else {
            val result = wsk.cli(Seq("list"), env = env, expectedExitCode = UNAUTHORIZED)
        }
    }

    behavior of "Wsk Package CLI"

    it should "list shared packages" in {
        val result = wsk.pkg.list(Some("/whisk.system")).stdout
        result should include regex ("""/whisk.system/samples\s+shared""")
        result should include regex ("""/whisk.system/util\s+shared""")
    }

    it should "list shared package actions" in {
        val result = wsk.action.list(Some("/whisk.system/util")).stdout
        result should include regex ("""/whisk.system/util/head\s+shared""")
        result should include regex ("""/whisk.system/util/date\s+shared""")
    }

    it should "create, update, get and list a package" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "samplePackage"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) =>
                    pkg.create(name, parameters = params, shared = Some(true))
                    pkg.create(name, update = true)
            }
            val stdout = wsk.pkg.get(name).stdout
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (""""publish": true""")
            stdout should include regex (""""version": "0.0.2"""")
            wsk.pkg.list().stdout should include(name)
    }

    it should "create a package binding" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "bindPackage"
            val provider = "/whisk.system/samples"
            val annotations = Map("a" -> "A".toJson, WhiskPackage.bindingFieldName -> "xxx".toJson)
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) =>
                    pkg.bind(provider, name, annotations = annotations)
            }
            val stdout = wsk.pkg.get(name).stdout
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (s""""key": "${WhiskPackage.bindingFieldName}"""")
            stdout should not include regex(""""key": "xxx"""")
    }

    behavior of "Wsk Action CLI"

    it should "create the same action twice with different cases" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.action, "TWICE") { (action, name) => action.create(name, defaultAction) }
            assetHelper.withCleaner(wsk.action, "twice") { (action, name) => action.create(name, defaultAction) }
    }

    it should "create, update, get and list an action" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "createAndUpdate"
            val file = Some(TestUtils.getCatalogFilename("samples/hello.js"))
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, file, parameters = params, shared = Some(true))
                    action.create(name, None, parameters = Map("b" -> "B".toJson), update = true)
            }
            val stdout = wsk.action.get(name).stdout
            stdout should not include regex (""""key": "a"""")
            stdout should not include regex (""""value": "A"""")
            stdout should include regex (""""key": "b""")
            stdout should include regex (""""value": "B"""")
            stdout should include regex (""""publish": true""")
            stdout should include regex (""""version": "0.0.2"""")
            wsk.action.list().stdout should include(name)
    }

    it should "get an action" in {
        wsk.action.get("/whisk.system/samples/wordCount").
            stdout should include("words")
    }

    it should "reject delete of action that does not exist" in {
        wsk.action.sanitize("deleteFantasy").
            stderr should include regex ("""error: The requested resource does not exist. \(code \d+\)""")
    }

    it should "reject create with missing file" in {
        wsk.action.create("missingFile", Some("notfound"),
            expectedExitCode = MISUSE_EXIT).
          stderr should include("not a valid file")
    }

    it should "reject action update when specified file is missing" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
          // Create dummy action to update
          val name = "updateMissingFile"
          val file = Some(TestUtils.getCatalogFilename("samples/hello.js"))
          assetHelper.withCleaner(wsk.action, name) { (action, name) => action.create(name, file) }
          // Update it with a missing file
          wsk.action.create("updateMissingFile", Some("notfound"), update = true, expectedExitCode = MISUSE_EXIT)
    }

    /**
     * Tests creating an action from a malformed js file. This should fail in
     * some way - preferably when trying to create the action. If not, then
     * surely when it runs there should be some indication in the logs. Don't
     * think this is true currently.
     */
    it should "create and invoke action with malformed js resulting in activation error" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "MALFORMED"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("malformed.js")))
            }

            val run = wsk.action.invoke(name, Map("payload" -> "whatever".toJson))
            withActivation(wsk.activation, run) {
                activation =>
                    activation.fields("response").asJsObject.fields("status") should be("action developer error".toJson)
                    // representing nodejs giving an error when given malformed.js
                    activation.fields("response").asJsObject.toString should include("ReferenceError")
            }
    }

    it should "invoke a blocking action and get only the result" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "basicInvoke"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getCatalogFilename("samples/wc.js")))
            }
            wsk.action.invoke(name, Map("payload" -> "one two three".toJson), blocking = true, result = true)
                .stdout should include regex (""""count": 3""")
    }

    behavior of "Wsk Trigger CLI"

    it should "create, update, get, fire and list trigger" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "listTriggers"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name, parameters = params, shared = Some(true))
                    trigger.create(name, update = true)
            }
            val stdout = wsk.trigger.get(name).stdout
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (""""publish": true""")
            stdout should include regex (""""version": "0.0.2"""")

            val dynamicParams = Map("t" -> "T".toJson)
            val run = wsk.trigger.fire(name, dynamicParams)
            withActivation(wsk.activation, run) {
                activation =>
                    activation.fields("response").asJsObject.fields("result") should be(dynamicParams.toJson)
                    activation.fields("end") should be(Instant.EPOCH.toEpochMilli.toJson)
            }

            wsk.trigger.list().stdout should include(name)
    }

    it should "not create a trigger when feed fails to initialize" in {
        val name = "badfeed"
        wsk.trigger.create(name, feed = Some(s"bogus"), expectedExitCode = ANY_ERROR_EXIT).
            exitCode should { equal(NOT_FOUND) or equal(FORBIDDEN) }
        wsk.trigger.get(name, expectedExitCode = NOT_FOUND)

        wsk.trigger.create(name, feed = Some(s"bogus/feed"), expectedExitCode = ANY_ERROR_EXIT).
            exitCode should { equal(NOT_FOUND) or equal(FORBIDDEN) }
        wsk.trigger.get(name, expectedExitCode = NOT_FOUND)

        // verify that the feed runs and returns an application error (502 or Gateway Timeout)
        wsk.trigger.create(name, feed = Some(s"/whisk.system/github/webhook"), expectedExitCode = TIMEOUT)
        wsk.trigger.get(name, expectedExitCode = NOT_FOUND)
    }

    behavior of "Wsk Rule CLI"

    it should "create rule, get rule, update rule and list rule" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "listRules"
            val triggerName = "listRulesTrigger"
            val actionName = "listRulesAction";
            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName) {
                (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
                    rule.create(name, trigger = triggerName, action = actionName, update = true)
            }

            val stdout = wsk.rule.get(ruleName).stdout
            stdout should include(ruleName)
            stdout should include(triggerName)
            stdout should include(actionName)
            stdout should include regex (""""version": "0.0.2"""")
            wsk.rule.list().stdout should include(ruleName)
    }

    behavior of "Wsk Namespace CLI"

    it should "list namespaces" in {
        wsk.namespace.list().
            stdout should include regex ("@|guest")
    }

    it should "list entities in default namespace" in {
        // use a fresh wsk props instance that is guaranteed to use
        // the default namespace
        wsk.namespace.get(expectedExitCode = SUCCESS_EXIT)(WskProps()).
            stdout should include("default")
    }
}

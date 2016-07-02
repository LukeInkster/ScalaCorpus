package mesosphere.marathon.api.validation

import com.wix.accord.validate
import mesosphere.marathon.Protos.{ Constraint, HealthCheckDefinition }
import mesosphere.marathon._
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon.api.v2.json.Formats
import mesosphere.marathon.core.plugin.{ PluginDefinitions, PluginManager }
import mesosphere.marathon.core.readiness.ReadinessCheck
import mesosphere.marathon.health.HealthCheck
import mesosphere.marathon.state.Container.Docker
import mesosphere.marathon.state._
import org.apache.mesos.{ Protos => mesos }
import org.scalatest.{ GivenWhenThen, Matchers }
import play.api.libs.json.Json

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

class RunSpecValidatorTest extends MarathonSpec with Matchers with GivenWhenThen {

  implicit lazy val validAppDefinition = AppDefinition.validAppDefinition(PluginManager.None)

  test("only cmd") {
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"))
    validate(app)
    MarathonTestHelper.validateJsonSchema(app)
  }

  private[this] def testValidId(id: String): Unit = {
    val app = AppDefinition(
      id = PathId(id),
      cmd = Some("true"))

    validate(app)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("id '/app' is valid") {
    testValidId("/app")
  }

  test("id '/hy-phenated' is valid") {
    testValidId("/hy-phenated")
  }

  test("id '/numbered9' is valid") {
    testValidId("/numbered9")
  }

  test("id '/9numbered' is valid") {
    testValidId("/9numbered")
  }

  test("id '/num8bered' is valid") {
    testValidId("/num8bered")
  }

  test("id '/dot.ted' is valid") {
    testValidId("/dot.ted")
  }

  test("id '/deep/ly/nes/ted' is valid") {
    testValidId("/deep/ly/nes/ted")
  }

  test("id '/all.to-9gether/now-huh12/nest/nest' is valid") {
    testValidId("/all.to-9gether/now-huh12/nest/nest")
  }

  test("id '/trailing/' is valid") {
    // the trailing slash is apparently ignored by Marathon
    testValidId("/trailing/")
  }

  test("single dots in id '/test/.' pass schema and validation") {
    testInvalid("/test/.")
  }

  test("single dots in id '/./not.point.less' pass schema and validation") {
    testInvalid("/./not.point.less")
  }

  private[this] def testSchemaLessStrictForId(id: String): Unit = {
    val app = AppDefinition(
      id = PathId(id),
      cmd = Some("true"))

    an[ValidationFailedException] should be thrownBy validateOrThrow(app)

    MarathonTestHelper.validateJsonSchema(app)
  }

  // non-absolute paths (could be allowed in some contexts)
  test(s"relative id 'relative/asd' passes schema but not validation") {
    testSchemaLessStrictForId("relative/asd")
  }

  // non-absolute paths (could be allowed in some contexts)
  test(s"relative id '../relative' passes schema but not validation") {
    testSchemaLessStrictForId("../relative")
  }

  private[this] def testInvalid(id: String): Unit = {
    val app = AppDefinition(
      id = PathId(id),
      cmd = Some("true")
    )

    val result = validate(app)
    result.isFailure should be(true)

    MarathonTestHelper.validateJsonSchema(app, valid = false)
  }

  test("id '/.../asd' is INVALID") {
    testInvalid("/.../asd")
  }

  test("id '/app!' is INVALID") {
    testInvalid("/app!' i")
  }

  test("id '/app[' is INVALID") {
    testInvalid("/app[' i")
  }

  test("id '/asd/sadf+' is INVALID") {
    testInvalid("/asd/sadf+")
  }

  test("id '/asd asd' is INVALID") {
    testInvalid("/asd asd")
  }

  test("id '/app-' is invalid because hyphens and dots are only allowed inside of path fragments") {
    testInvalid("/app-")
  }

  test("id '/nest./ted' is invalid because hyphens and dots are only allowed inside of path fragments") {
    testInvalid("/nest./ted")
  }

  test("id '/nest/-ted' is invalid because hyphens and dots are only allowed inside of path fragments") {
    testInvalid("/nest/-ted")
  }

  test("only cmd + command health check") {
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      healthChecks = Set(
        HealthCheck(
          protocol = HealthCheckDefinition.Protocol.COMMAND,
          command = Some(Command("curl http://localhost:$PORT"))
        )
      )
    )
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("only cmd + acceptedResourceRoles") {
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      acceptedResourceRoles = Some(Set(ResourceRole.Unreserved)))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("only cmd + acceptedResourceRoles 2") {
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      acceptedResourceRoles = Some(Set(ResourceRole.Unreserved, "production")))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("only args") {
    val app = AppDefinition(
      id = PathId("/test"),
      args = Some("test" :: Nil))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("only container") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      container = Some(f.validDockerContainer))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("empty container is invalid") {
    val app = AppDefinition(
      id = PathId("/test"),
      container = Some(Container()))
    assert(validate(app).isFailure)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("container with type DOCKER and empty docker field is invalid") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      container = Some(f.invalidDockerContainer))
    assert(validate(app).isFailure)
    MarathonTestHelper.validateJsonSchema(app, valid = true)
  }

  test("container and cmd") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      container = Some(f.validDockerContainer))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("container and args") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      args = Some("test" :: Nil),
      container = Some(f.validDockerContainer))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app)
  }

  test("container, cmd and args is not valid") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      args = Some("test" :: Nil),
      container = Some(f.validDockerContainer))
    assert(validate(app).isFailure)
    MarathonTestHelper.validateJsonSchema(app, valid = false)
  }

  test("container with type MESOS and nonEmpty docker field is invalid") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      container = Some(f.invalidMesosContainer))
    assert(validate(app).isFailure)
    MarathonTestHelper.validateJsonSchema(app, valid = true)
  }

  test("container with type MESOS and empty docker field is valid") {
    val f = new Fixture
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      container = Some(f.validMesosContainer))
    assert(validate(app).isSuccess)
    MarathonTestHelper.validateJsonSchema(app, valid = true)
  }

  test("valid docker volume") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume)
    )
    assert(validate(container).isSuccess)
  }

  test("docker volume with missing containerPath is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validDockerVolume.copy(containerPath = ""))
    )
    assert(validate(container).isFailure)
  }

  test("docker volume with missing hostPath is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validDockerVolume.copy(hostPath = ""))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with missing containerPath is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = ""))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with mode RO is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(mode = mesos.Volume.Mode.RO))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with size 0 is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(persistent = PersistentVolumeInfo(0)))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with size < 0 is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(persistent = PersistentVolumeInfo(-1)))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with container path '.' is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = "."))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with container path '..' is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = ".."))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with container path '.hidden' is valid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = ".hidden"))
    )
    assert(validate(container).isSuccess)
  }

  test("persistent volume with container path with dots in the middle is valid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = "foo..bar"))
    )
    assert(validate(container).isSuccess)
  }

  test("persistent volume with container path starting with a forward slash is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = "/path"))
    )
    assert(validate(container).isFailure)
  }

  test("persistent volume with container path containing forward slashes is invalid") {
    val f = new Fixture
    val container = f.validDockerContainer.copy(
      volumes = Seq(f.validPersistentVolume.copy(containerPath = "foo/bar"))
    )
    assert(validate(container).isFailure)
  }

  test("Validation for update of resident apps") {
    Given("A resident app definition")
    val f = new Fixture
    val from = f.validResident

    When("Check if update to itself is valid")
    val to = from
    Then("Should be valid")
    AppDefinition.residentUpdateIsValid(from)(to).isSuccess should be(true)

    When("Check if default upgrade strategy is valid")
    val to2 = from.copy(upgradeStrategy = AppDefinition.DefaultUpgradeStrategy)
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to2).isSuccess should be(false)

    When("Check if removing a volume is valid")
    val to3 = f.residentApp(from.id.toString, Seq(f.vol1))
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to3).isSuccess should be(false)

    When("Check if adding a volume is valid")
    val to4 = f.residentApp(from.id.toString, Seq(f.vol1, f.vol2, f.vol3))
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to4).isSuccess should be(false)

    When("Check if changing a volume is valid")
    val to5 = f.residentApp(from.id.toString, Seq(f.vol1, f.vol3))
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to5).isSuccess should be(false)

    When("Check if changing mem is valid")
    val to6 = from.copy(mem = 123)
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to6).isSuccess should be(false)

    When("Check if changing cpu is valid")
    val to7 = from.copy(cpus = 123)
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to7).isSuccess should be(false)

    When("Check if changing disk is valid")
    val to8 = from.copy(disk = 123)
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to8).isSuccess should be(false)

    When("Check if changing ports is valid")
    val to9 = from.copy(portDefinitions = Seq.empty)
    Then("Should be invalid")
    AppDefinition.residentUpdateIsValid(from)(to9).isSuccess should be(false)
  }

  test("Validation for defining a resident app") {
    Given("A resident app definition")
    val f = new Fixture
    val from = f.validResident

    When("Check if only defining residency without persistent volumes is valid")
    val to1 = from.copy(container = None)
    Then("Should be invalid")
    validAppDefinition(to1).isSuccess should be(false)

    When("Check if only defining local volumes without residency is valid")
    val to2 = from.copy(residency = None)
    Then("Should be invalid")
    validAppDefinition(to2).isSuccess should be(false)

    When("Check if defining local volumes and residency is valid")
    Then("Should be valid")
    validAppDefinition(from).isSuccess should be(true)

    When("Check if defining no local volumes and no residency is valid")
    val to3 = from.copy(residency = None, container = None)
    Then("Should be valid")
    validAppDefinition(to3).isSuccess should be(true)
  }

  test("A application with label MARATHON_SINGLE_INSTANCE_APP may not have an instance count > 1") {
    Given("an app with label MARATHON_SINGLE_INSTANCE_APP and an instance count of 0")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      instances = 0,
      upgradeStrategy = UpgradeStrategy(0, 0),
      labels = Map[String, String](
        AppDefinition.Labels.SingleInstanceApp -> true.toString
      )
    )
    Then("the validation succeeds")
    validAppDefinition(app).isSuccess shouldBe true

    When("the instance count is set to 1")
    val appWith1Instance = app.copy(instances = 1)
    Then("the validation succeeds")
    validAppDefinition(appWith1Instance).isSuccess shouldBe true

    When("the instance count is set to 2")
    val appWith2Instances = app.copy(instances = 2)
    Then("the validation fails")
    validAppDefinition(appWith2Instances).isFailure shouldBe true
  }

  test("For an application with label MARATHON_SINGLE_INSTANCE_APP UpgradeStrategy(1,0) is invalid") {
    Given("an app with label MARATHON_SINGLE_INSTANCE_APP and an UpgradeStrategy(1,0)")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(1, 0),
      labels = Map[String, String](
        AppDefinition.Labels.SingleInstanceApp -> true.toString
      )
    )
    Then("the validation fails")
    validAppDefinition(app).isFailure shouldBe true
  }

  test("For an application with label MARATHON_SINGLE_INSTANCE_APP UpgradeStrategy(1,1) is invalid") {
    Given("an app with label MARATHON_SINGLE_INSTANCE_APP and an UpgradeStrategy(1,1)")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(1, 1),
      labels = Map[String, String](
        AppDefinition.Labels.SingleInstanceApp -> true.toString
      )
    )
    Then("the validation fails")
    validAppDefinition(app).isFailure shouldBe true
  }

  test("For an application with label MARATHON_SINGLE_INSTANCE_APP UpgradeStrategy(0,1) is invalid") {
    Given("an app with label MARATHON_SINGLE_INSTANCE_APP and an UpgradeStrategy(0,1)")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(0, 1),
      labels = Map[String, String](
        AppDefinition.Labels.SingleInstanceApp -> true.toString
      )
    )
    Then("the validation fails")
    validAppDefinition(app).isFailure shouldBe true
  }

  test("For an application with label MARATHON_SINGLE_INSTANCE_APP UpgradeStrategy(0,0) is valid") {
    Given("an app with label MARATHON_SINGLE_INSTANCE_APP and an UpgradeStrategy(0,0)")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(0, 0),
      labels = Map[String, String](
        AppDefinition.Labels.SingleInstanceApp -> true.toString
      )
    )
    Then("the validation fails")
    validAppDefinition(app).isSuccess shouldBe true
  }

  test("readinessChecks are invalid for normal apps") {
    Given("a normal app with a defined readinessCheck")
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      readinessChecks = Seq(ReadinessCheck()))

    Then("validation fails")
    validAppDefinition(app).isFailure shouldBe true
  }

  test("validation of constraints") {
    import Constraint.Operator._

    val unique = Constraint.newBuilder.setField("hostname").setOperator(UNIQUE)
    val cluster = Constraint.newBuilder.setField("rack-id").setOperator(CLUSTER)
    val groupBy = Constraint.newBuilder.setField("rack-id").setOperator(GROUP_BY)
    val like = Constraint.newBuilder.setField("rack-id").setOperator(LIKE)
    val unlike = Constraint.newBuilder.setField("rack-id").setOperator(UNLIKE)
    val max_per = Constraint.newBuilder.setField("rack-id").setOperator(MAX_PER)

    Given("no constraints")
    val app = AppDefinition(
      id = PathId("/test"),
      cmd = Some("true"),
      constraints = Set())
    Then("validation succeeds")
    validAppDefinition(app).isSuccess shouldBe true

    Given("A UNIQUE constraint without a value")
    val appUnique = app.copy(constraints = Set(unique.build()))
    Then("validation succeeds")
    validAppDefinition(appUnique).isSuccess shouldBe true

    Given("A UNIQUE constraint with a value")
    val appUniqueValue = app.copy(constraints = Set(unique.setValue("a").build()))
    Then("validation fails")
    validAppDefinition(appUniqueValue).isFailure shouldBe true

    Given("A CLUSTER constraint without a value")
    val appClusterNoValue = app.copy(constraints = Set(cluster.build()))
    Then("validation fails")
    validAppDefinition(appClusterNoValue).isFailure shouldBe true

    Given("A CLUSTER constraint with a value")
    val appCluster = app.copy(constraints = Set(cluster.setValue("abc").build()))
    Then("validation succeeds")
    validAppDefinition(appCluster).isSuccess shouldBe true

    Given("A GROUP_BY without a value")
    val appGroupByNoValue = app.copy(constraints = Set(groupBy.build()))
    Then("validation succeeds")
    validAppDefinition(appGroupByNoValue).isSuccess shouldBe true

    Given("A GROUP_BY with a numeric value")
    val appGroupByNumericValue = app.copy(constraints = Set(groupBy.setValue("123").build()))
    Then("validation succeeds")
    validAppDefinition(appGroupByNumericValue).isSuccess shouldBe true

    Given("A GROUP_BY with a non-numeric value")
    val appGroupByNonNumericValue = app.copy(constraints = Set(groupBy.setValue("AbcDZ").build()))
    Then("validation fails")
    validAppDefinition(appGroupByNonNumericValue).isFailure shouldBe true

    Given("A MAX_PER with a numeric value")
    val appMaxPerNumericValue = app.copy(constraints = Set(max_per.setValue("123").build()))
    Then("validation succeeds")
    validAppDefinition(appMaxPerNumericValue).isSuccess shouldBe true

    Given("A MAX_PER with a non-numeric value")
    val appMaxPerNonNumericValue = app.copy(constraints = Set(max_per.setValue("AbcDZ").build()))
    Then("validation fails")
    validAppDefinition(appMaxPerNonNumericValue).isFailure shouldBe true

    Seq(like, unlike).foreach { op =>
      Given(s"A ${op.getOperator} without a value")
      val appOpNoValue = app.copy(constraints = Set(op.build()))
      Then("validation fails")
      validAppDefinition(appOpNoValue).isFailure shouldBe true

      Given(s"A ${op.getOperator} with a valid regex")
      val appOpRegex = app.copy(constraints = Set(op.setValue(".*").build()))
      Then("validation succeeds")
      validAppDefinition(appOpRegex).isSuccess shouldBe true

      Given(s"A ${op.getOperator} with an invalid regex")
      val appOpBadRegex = app.copy(constraints = Set(op.setValue("*").build()))
      Then("validation fails")
      validAppDefinition(appOpBadRegex).isFailure shouldBe true
    }
  }

  test("Resident app may only define unreserved acceptedResourceRoles or None") {
    Given("A resident app definition")
    val f = new Fixture
    val from = f.validResident

    When("validating with role for static reservation")
    val to1 = from.copy(acceptedResourceRoles = Some(Set("foo")))
    Then("Should be invalid")
    validAppDefinition(to1).isSuccess shouldBe false

    When("validating with only unreserved roles")
    val to2 = from.copy(acceptedResourceRoles = Some(Set(ResourceRole.Unreserved)))
    Then("Should be valid")
    validAppDefinition(to2).isSuccess shouldBe true

    When("validating without acceptedResourceRoles")
    val to3 = from.copy(acceptedResourceRoles = None)
    Then("Should be valid")
    validAppDefinition(to3).isSuccess shouldBe true
  }

  test("health check validation should allow port specifications without port indices") {
    Given("A docker app with no portDefinitions and HTTP health checks")

    val app1 = AppDefinition(
      container = Some(
        Container(docker = Some(
          Container.Docker(
            "group/image",
            network = Some(mesos.ContainerInfo.DockerInfo.Network.HOST)
          )))
      ),
      portDefinitions = List.empty,
      healthChecks = Set(
        HealthCheck(
          path = Some("/"),
          protocol = Protos.HealthCheckDefinition.Protocol.HTTP,
          port = Some(8000),
          portIndex = None
        )
      )
    )
    Then("validation succeeds")
    validAppDefinition(app1).isSuccess shouldBe true
  }

  test("cassandraWithoutResidency") {
    import Formats._

    val f = new Fixture
    val app = Json.parse(f.cassandraWithoutResidency).as[AppDefinition]
    val result = validAppDefinition(app)
    result.isSuccess shouldBe true
  }

  test("cassandraWithoutResidencyWithUpgradeStrategy") {
    import Formats._

    val f = new Fixture
    val base = Json.parse(f.cassandraWithoutResidency).as[AppDefinition]
    val app = base.copy(upgradeStrategy = UpgradeStrategy(0, 0))
    val result = validAppDefinition(app)
    result.isSuccess shouldBe true
  }

  test("Validation plugins can invalidate apps") {
    Given("An app with an invalid label")
    val app = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(0, 0),
      env = Map[String, EnvVarValue]("SECURITY_USER" -> new EnvVarString("admin"))
    )
    Then("the validation fails")
    val pm = new PluginManager() {
      def plugins[T](implicit ct: ClassTag[T]): Seq[T] = {
        ct.toString() match {
          case "mesosphere.marathon.plugin.validation.RunSpecValidator" =>
            List(
              isTrue[mesosphere.marathon.plugin.RunSpec]("SECURITY_* environment variables are not permitted") {
              _.env.keys.count(_.startsWith("SECURITY_")) == 0
            }.asInstanceOf[T]
            )
          case _ => List.empty
        }
      }
      def definitions: PluginDefinitions = PluginDefinitions.None
    }
    AppDefinition.validAppDefinition(pm)(app).isFailure shouldBe true

    Given("An app without an invalid label")
    val app2 = AppDefinition(
      cmd = Some("sleep 1000"),
      upgradeStrategy = UpgradeStrategy(0, 0),
      env = EnvVarValue(Map[String, String](
        "APP_USER" -> "admin"
      ))
    )
    Then("the validation succeeds")
    AppDefinition.validAppDefinition(pm)(app2).isSuccess shouldBe true
  }

  class Fixture {
    def validDockerContainer: Container = Container(
      `type` = mesos.ContainerInfo.Type.DOCKER,
      volumes = Nil,
      docker = Some(Docker(image = "foo/bar:latest"))
    )

    def invalidDockerContainer: Container = Container(
      `type` = mesos.ContainerInfo.Type.DOCKER,
      volumes = Nil,
      docker = None
    )

    def validMesosContainer: Container = Container(
      `type` = mesos.ContainerInfo.Type.MESOS,
      volumes = Nil,
      docker = None
    )

    def invalidMesosContainer: Container = Container(
      `type` = mesos.ContainerInfo.Type.MESOS,
      volumes = Nil,
      docker = Some(Docker(image = "foo/bar:latest"))
    )

    // scalastyle:off magic.number
    def validPersistentVolume: PersistentVolume = PersistentVolume(
      containerPath = "test",
      persistent = PersistentVolumeInfo(10),
      mode = mesos.Volume.Mode.RW)

    def validDockerVolume: DockerVolume = DockerVolume(
      containerPath = "/test",
      hostPath = "/etc/foo",
      mode = mesos.Volume.Mode.RW)

    def persistentVolume(path: String) = PersistentVolume(path, PersistentVolumeInfo(123), mesos.Volume.Mode.RW)
    val zero = UpgradeStrategy(0, 0)

    def residentApp(id: String, volumes: Seq[PersistentVolume]): AppDefinition = {
      AppDefinition(
        id = PathId(id),
        cmd = Some("test"),
        container = Some(Container(mesos.ContainerInfo.Type.MESOS, volumes)),
        residency = Some(Residency(123, Protos.ResidencyDefinition.TaskLostBehavior.RELAUNCH_AFTER_TIMEOUT))
      )
    }
    val vol1 = persistentVolume("foo")
    val vol2 = persistentVolume("bla")
    val vol3 = persistentVolume("test")
    val validResident = residentApp("/app1", Seq(vol1, vol2)).copy(upgradeStrategy = zero)

    def cassandraWithoutResidency =
      """
        |{
        |  "id": "/cassandra",
        |  "cpus": 2,
        |  "mem": 2048,
        |  "instances": 1,
        |  "constraints": [
        |    [
        |      "hostname",
        |      "UNIQUE"
        |    ]
        |  ],
        |  "container": {
        |    "type": "DOCKER",
        |    "docker": {
        |      "image": "tobert/cassandra",
        |      "network": "BRIDGE",
        |      "forcePullImage": true,
        |      "portMappings": [
        |        {
        |          "containerPort": 7000,
        |          "hostPort": 7000,
        |          "protocol": "tcp"
        |        },
        |        {
        |          "containerPort": 7199,
        |          "hostPort": 7199,
        |          "protocol": "tcp"
        |        },
        |        {
        |          "containerPort": 9042,
        |          "hostPort": 9042,
        |          "protocol": "tcp"
        |        },
        |        {
        |          "containerPort": 9160,
        |          "hostPort": 9160,
        |          "protocol": "tcp"
        |        }
        |      ]
        |    },
        |    "volumes": [
        |      {
        |        "containerPath": "/data",
        |        "hostPath": "cassandradata",
        |        "mode": "RW"
        |      },
        |      {
        |        "containerPath": "cassandradata",
        |        "mode": "RW",
        |        "persistent": {
        |          "size": 1000
        |        }
        |      }
        |    ]
        |  },
        |  "healthChecks": [
        |    {
        |      "protocol": "TCP",
        |      "portIndex": 3,
        |      "gracePeriodSeconds": 5,
        |      "intervalSeconds": 20,
        |      "maxConsecutiveFailures": 3
        |    }
        |  ]
        |}
      """.stripMargin
  }
}

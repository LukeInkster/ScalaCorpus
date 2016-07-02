package lila.round

import akka.actor._
import akka.pattern.ask
import com.typesafe.config.Config
import scala.concurrent.duration._

import actorApi.{ GetSocketStatus, SocketStatus }
import lila.common.PimpedConfig._
import lila.game.{ Game, Pov }
import lila.hub.actorApi.HasUserId
import lila.hub.actorApi.map.{ Ask, Tell }
import lila.socket.actorApi.GetVersion
import makeTimeout.large

final class Env(
    config: Config,
    system: ActorSystem,
    db: lila.db.Env,
    hub: lila.hub.Env,
    fishnetPlayer: lila.fishnet.Player,
    aiPerfApi: lila.fishnet.AiPerfApi,
    crosstableApi: lila.game.CrosstableApi,
    playban: lila.playban.PlaybanApi,
    lightUser: String => Option[lila.common.LightUser],
    userJsonView: lila.user.JsonView,
    rankingApi: lila.user.RankingApi,
    notifyApi: lila.notify.NotifyApi,
    uciMemo: lila.game.UciMemo,
    rematch960Cache: lila.memo.ExpireSetMemo,
    isRematchCache: lila.memo.ExpireSetMemo,
    onStart: String => Unit,
    divider: lila.game.Divider,
    i18nKeys: lila.i18n.I18nKeys,
    prefApi: lila.pref.PrefApi,
    historyApi: lila.history.HistoryApi,
    scheduler: lila.common.Scheduler) {

  private val settings = new {
    val UidTimeout = config duration "uid.timeout"
    val PlayerDisconnectTimeout = config duration "player.disconnect.timeout"
    val PlayerRagequitTimeout = config duration "player.ragequit.timeout"
    val AnimationDuration = config duration "animation.duration"
    val Moretime = config duration "moretime"
    val SocketName = config getString "socket.name"
    val SocketTimeout = config duration "socket.timeout"
    val NetDomain = config getString "net.domain"
    val ActorMapName = config getString "actor.map.name"
    val CasualOnly = config getBoolean "casual_only"
    val ActiveTtl = config duration "active.ttl"
    val CollectionNote = config getString "collection.note"
    val CollectionHistory = config getString "collection.history"
    val CollectionForecast = config getString "collection.forecast"
    val ChannelMoveTime = config getString "channel.move_time.name "
  }
  import settings._

  private val moveTimeChannel = system.actorOf(Props(classOf[lila.socket.Channel]), name = ChannelMoveTime)

  lazy val eventHistory = History(db(CollectionHistory)) _

  val roundMap = system.actorOf(Props(new lila.hub.ActorMap {
    def mkActor(id: String) = new Round(
      gameId = id,
      messenger = messenger,
      takebacker = takebacker,
      finisher = finisher,
      rematcher = rematcher,
      player = player,
      drawer = drawer,
      forecastApi = forecastApi,
      socketHub = socketHub,
      moretimeDuration = Moretime,
      activeTtl = ActiveTtl)
    def receive: Receive = ({
      case actorApi.GetNbRounds =>
        nbRounds = size
        system.lilaBus.publish(lila.hub.actorApi.round.NbRounds(nbRounds), 'nbRounds)
    }: Receive) orElse actorMapReceive
  }), name = ActorMapName)

  private var nbRounds = 0
  def count() = nbRounds

  private val socketHub = {
    val actor = system.actorOf(
      Props(new lila.socket.SocketHubActor[Socket] {
        private var historyPersistenceEnabled = false
        def mkActor(id: String) = new Socket(
          gameId = id,
          history = eventHistory(id, historyPersistenceEnabled),
          lightUser = lightUser,
          uidTimeout = UidTimeout,
          socketTimeout = SocketTimeout,
          disconnectTimeout = PlayerDisconnectTimeout,
          ragequitTimeout = PlayerRagequitTimeout,
          simulActor = hub.actor.simul)
        def receive: Receive = ({
          case msg@lila.chat.actorApi.ChatLine(id, line) =>
            self ! Tell(id take 8, msg)
          case _: lila.hub.actorApi.Deploy =>
            logger.warn("Enable history persistence")
            historyPersistenceEnabled = true
            // if the deploy didn't go through, cancel persistence
            system.scheduler.scheduleOnce(10.minutes) {
              logger.warn("Disabling round history persistence!")
              historyPersistenceEnabled = false
            }
          case msg: lila.game.actorApi.StartGame =>
            self ! Tell(msg.game.id, msg)
        }: Receive) orElse socketHubReceive
      }),
      name = SocketName)
    system.lilaBus.subscribe(actor, 'tvSelect, 'startGame, 'deploy)
    actor
  }

  lazy val socketHandler = new SocketHandler(
    hub = hub,
    roundMap = roundMap,
    socketHub = socketHub,
    messenger = messenger,
    bus = system.lilaBus)

  lazy val perfsUpdater = new PerfsUpdater(historyApi, rankingApi)

  lazy val forecastApi: ForecastApi = new ForecastApi(
    coll = db(CollectionForecast),
    roundMap = hub.actor.roundMap)

  private lazy val notifier = new RoundNotifier(
    timeline = hub.actor.timeline,
    isUserPresent = isUserPresent,
    notifyApi = notifyApi)

  private lazy val finisher = new Finisher(
    messenger = messenger,
    perfsUpdater = perfsUpdater,
    crosstableApi = crosstableApi,
    notifier = notifier,
    playban = playban,
    bus = system.lilaBus,
    casualOnly = CasualOnly)

  private lazy val rematcher = new Rematcher(
    messenger = messenger,
    onStart = onStart,
    rematch960Cache = rematch960Cache,
    isRematchCache = isRematchCache)

  private lazy val player: Player = new Player(
    fishnetPlayer = fishnetPlayer,
    bus = system.lilaBus,
    finisher = finisher,
    cheatDetector = cheatDetector,
    uciMemo = uciMemo)

  private lazy val drawer = new Drawer(
    prefApi = prefApi,
    messenger = messenger,
    finisher = finisher)

  private lazy val cheatDetector = new CheatDetector(reporter = hub.actor.report)

  lazy val messenger = new Messenger(
    chat = hub.actor.chat,
    i18nKeys = i18nKeys)

  def version(gameId: String): Fu[Int] =
    socketHub ? Ask(gameId, GetVersion) mapTo manifest[Int]

  private def getSocketStatus(gameId: String): Fu[SocketStatus] =
    socketHub ? Ask(gameId, GetSocketStatus) mapTo manifest[SocketStatus]

  private def isUserPresent(game: Game, userId: lila.user.User.ID): Fu[Boolean] =
    socketHub ? Ask(game.id, HasUserId(userId)) mapTo manifest[Boolean]

  lazy val jsonView = new JsonView(
    noteApi = noteApi,
    userJsonView = userJsonView,
    getSocketStatus = getSocketStatus,
    canTakeback = takebacker.isAllowedByPrefs,
    divider = divider,
    baseAnimationDuration = AnimationDuration,
    moretimeSeconds = Moretime.toSeconds.toInt)

  lazy val noteApi = new NoteApi(db(CollectionNote))

  MoveMonitor.start(system, moveTimeChannel)

  scheduler.message(2.1 seconds)(roundMap -> actorApi.GetNbRounds)

  system.actorOf(
    Props(classOf[Titivate], roundMap, hub.actor.bookmark),
    name = "titivate")

  lazy val takebacker = new Takebacker(
    messenger = messenger,
    uciMemo = uciMemo,
    prefApi = prefApi)

  val tvBroadcast = system.actorOf(Props(classOf[TvBroadcast]))
  system.lilaBus.subscribe(tvBroadcast, 'moveEvent, 'changeFeaturedGame)

  def checkOutoftime(game: Game) {
    if (game.playable && game.started && !game.isUnlimited)
      roundMap ! Tell(game.id, actorApi.round.Outoftime)
  }

  def resign(pov: Pov) {
    if (pov.game.abortable)
      roundMap ! Tell(pov.game.id, actorApi.round.Abort(pov.playerId))
    else if (pov.game.playable)
      roundMap ! Tell(pov.game.id, actorApi.round.Resign(pov.playerId))
  }
}

object Env {

  lazy val current = "round" boot new Env(
    config = lila.common.PlayApp loadConfig "round",
    system = lila.common.PlayApp.system,
    db = lila.db.Env.current,
    hub = lila.hub.Env.current,
    fishnetPlayer = lila.fishnet.Env.current.player,
    aiPerfApi = lila.fishnet.Env.current.aiPerfApi,
    crosstableApi = lila.game.Env.current.crosstableApi,
    playban = lila.playban.Env.current.api,
    lightUser = lila.user.Env.current.lightUser,
    userJsonView = lila.user.Env.current.jsonView,
    rankingApi = lila.user.Env.current.rankingApi,
    notifyApi = lila.notify.Env.current.api,
    uciMemo = lila.game.Env.current.uciMemo,
    rematch960Cache = lila.game.Env.current.cached.rematch960,
    isRematchCache = lila.game.Env.current.cached.isRematch,
    onStart = lila.game.Env.current.onStart,
    divider = lila.game.Env.current.divider,
    i18nKeys = lila.i18n.Env.current.keys,
    prefApi = lila.pref.Env.current.api,
    historyApi = lila.history.Env.current.api,
    scheduler = lila.common.PlayApp.scheduler)
}

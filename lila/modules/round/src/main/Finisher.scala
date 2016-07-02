package lila.round

import chess.Color._
import chess.Status._
import chess.{ Status, Color, Speed }

import lila.db.dsl._
import lila.game.actorApi.{ FinishGame, AbortedBy }
import lila.game.{ GameRepo, Game, Pov, Event }
import lila.i18n.I18nKey.{ Select => SelectI18nKey }
import lila.playban.{ PlaybanApi, Outcome }
import lila.user.{ User, UserRepo, Perfs }

private[round] final class Finisher(
    messenger: Messenger,
    perfsUpdater: PerfsUpdater,
    playban: PlaybanApi,
    notifier: RoundNotifier,
    crosstableApi: lila.game.CrosstableApi,
    bus: lila.common.Bus,
    casualOnly: Boolean) {

  def abort(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = apply(pov.game, _.Aborted) >>- {
    playban.abort(pov)
    bus.publish(AbortedBy(pov), 'abortGame)
  }

  def rageQuit(game: Game, winner: Option[Color])(implicit proxy: GameProxy): Fu[Events] =
    apply(game, _.Timeout, winner) >>- winner.?? { color => playban.rageQuit(game, !color) }

  def outOfTime(game: Game)(implicit proxy: GameProxy): Fu[Events] = {
    import lila.common.PlayApp
    if (!PlayApp.startedSinceSeconds(120) && game.updatedAt.exists(_ isBefore PlayApp.startedAt)) {
      logger.info(s"Aborting game last played before JVM boot: ${game.id}")
      other(game, _.Aborted, none)
    }
    else {
      val winner = Some(!game.player.color) filterNot { color =>
        game.toChess.board.variant.insufficientWinningMaterial(game.toChess.situation.board, color)
      }
      other(game, _.Outoftime, winner)
    }
  }

  def other(
    game: Game,
    status: Status.type => Status,
    winner: Option[Color] = None,
    message: Option[SelectI18nKey] = None)(implicit proxy: GameProxy): Fu[Events] =
    apply(game, status, winner, message) >>- playban.goodFinish(game)

  private def apply(
    game: Game,
    makeStatus: Status.type => Status,
    winner: Option[Color] = None,
    message: Option[SelectI18nKey] = None)(implicit proxy: GameProxy): Fu[Events] = {
    val status = makeStatus(Status)
    val prog = game.finish(status, winner)
    if (game.nonAi && game.isCorrespondence) Color.all foreach notifier.gameEnd(prog.game)
    lila.mon.game.finish(status.name)()
    casualOnly.fold(
      GameRepo unrate prog.game.id inject prog.game.copy(mode = chess.Mode.Casual),
      fuccess(prog.game)
    ) flatMap { g =>
        proxy.save(prog) >>
          GameRepo.finish(
            id = g.id,
            winnerColor = winner,
            winnerId = winner flatMap (g.player(_).userId),
            status = prog.game.status) >>
            UserRepo.pair(
              g.whitePlayer.userId,
              g.blackPlayer.userId).flatMap {
                case (whiteO, blackO) => {
                  val finish = FinishGame(g, whiteO, blackO)
                  updateCountAndPerfs(finish) inject {
                    message foreach { messenger.system(g, _) }
                    GameRepo game g.id foreach { newGame =>
                      bus.publish(finish.copy(game = newGame | g), 'finishGame)
                    }
                    prog.events
                  }
                }
              }
      }
  } >>- proxy.invalidate

  private def updateCountAndPerfs(finish: FinishGame): Funit =
    (!finish.isVsSelf && !finish.game.aborted) ?? {
      (finish.white |@| finish.black).tupled ?? {
        case (white, black) =>
          crosstableApi add finish.game zip perfsUpdater.save(finish.game, white, black)
      } zip
        (finish.white ?? incNbGames(finish.game)) zip
        (finish.black ?? incNbGames(finish.game)) void
    }

  private def incNbGames(game: Game)(user: User): Funit = game.finished ?? {
    val totalTime = user.playTime.isDefined option game.moveTimes.sum / 10
    val tvTime = totalTime ifTrue game.metadata.tvAt.isDefined
    UserRepo.incNbGames(user.id, game.rated, game.hasAi,
      result = if (game.winnerUserId exists (user.id==)) 1
      else if (game.loserUserId exists (user.id==)) -1
      else 0,
      totalTime = totalTime,
      tvTime = tvTime).void
  }
}

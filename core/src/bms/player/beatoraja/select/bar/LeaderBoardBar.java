package bms.player.beatoraja.select.bar;

import java.util.Arrays;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRResponse;
import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.ir.LeaderboardEntry;
import bms.player.beatoraja.ir.LR2IRConnection;
import bms.player.beatoraja.ir.LR2GhostData;
import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.pattern.GhostBattlePattern;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.BMSPlayerMode;
import javafx.util.Pair;

import static bms.player.beatoraja.select.bar.FunctionBar.*;

public class LeaderBoardBar extends DirectoryBar {
	private final SongData songData;
	private final String title;
	private final boolean fromLR2IR;

	public LeaderBoardBar(MusicSelector selector, SongData songData, boolean fromLR2IR) {
		super(selector);
		this.songData = songData;
		this.title = songData.getFullTitle();
		this.fromLR2IR = fromLR2IR;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public Bar[] getChildren() {
		// NOTE: For further devs, the leaderboard's children is sorted by 'exscore', if you want to implement a
		// different sort strategy, you need to change two 'fromIRScoreData' implementation
		if (!fromLR2IR) {
			MainController.IRStatus pir = selector.main.getIRStatus()[0];
			IRResponse<IRScoreData[]> response = pir.connection.getPlayData(pir.player, new IRChartData(songData));
			if (!response.isSucceeded()) {
				ImGuiNotify.error(String.format("Failed to load ir leaderboard: %s",response.getMessage()));
				return new Bar[0];
			}
			IRScoreData[] irScoreData = response.getData();
            LeaderboardEntry[] leaderboard = Arrays.stream(irScoreData)
                                                 .map(LeaderboardEntry::newEntryPrimaryIR)
                                                 .toArray(LeaderboardEntry[] ::new);
            return fromIRScoreData(leaderboard);
		} else {
			Pair<IRScoreData, LeaderboardEntry[]> scores = LR2IRConnection.getScoreData(new IRChartData(songData));
			IRScoreData localScore = scores.getKey();
			LeaderboardEntry[] scoreData = scores.getValue();
			if (localScore != null) {
				return fromIRScoreData(localScore, scoreData);
			}
			return fromIRScoreData(scoreData);
		}
	}

	/**
	 * Convert some ir scores to bars
	 *
	 * @param irScoreData ir scores, should be ordered by exscore. More specifically, the score has larger exscore
	 *                    should be positioned before a smaller one
	 * @return bars
	 */
	public FunctionBar[] fromIRScoreData(LeaderboardEntry[] irScoreData) {
		FunctionBar[] bars = new FunctionBar[irScoreData.length];
		for (int i = 0; i < irScoreData.length; i++) {
			bars[i] = createFunctionBar(i + 1, irScoreData[i], false);
		}
		return bars;
	}

	/**
	 * Convert some ir scores to bars and insert the local score into. Used for
	 *
	 * @param localScore  local score, would be inserted into scores
	 * @param irScoreData ir scores, should be ordered by exscore. More specifically, the score has larger exscore
	 *                    should be positioned before a smaller one
	 * @return bars
	 * @implNote This function is the reason why ir scores need to be sorted before the function call, because we need
	 * to insert the local score into it. An alternative idea is marking the local score so we can mix them and sort,
	 * and tell which one is the local score to give it a specific bar type. However, this needs to change the original
	 * class fields to achieve. (But actually, this isn't impossible if we have a good pair type implementation, we can
	 * attach a boolean with score and sort by score's exscore)
	 */
	public FunctionBar[] fromIRScoreData(IRScoreData localScore, LeaderboardEntry[] leaderboard) {
		FunctionBar[] bars = new FunctionBar[leaderboard.length + 1];
		int id = 0;
		boolean inserted = false;
        FunctionBar playerBar = createFunctionBar(id, LeaderboardEntry.newEntryPrimaryIR(localScore), true);
        if (leaderboard.length == 0 || localScore.getExscore() > leaderboard[0].getIrScore().getExscore()) {
            id++;
			bars[0] = playerBar;
			inserted = true;
        }
        for (int i = 0; i < leaderboard.length; i++) {
			LeaderboardEntry entry = leaderboard[i];
			IRScoreData score = entry.getIrScore();
			bars[id] = createFunctionBar(id + 1, entry, false);
			id++;
            if (!inserted && score.getExscore() > localScore.getExscore() &&
                (i == leaderboard.length - 1 || leaderboard[i + 1].getIrScore().getExscore() <= localScore.getExscore())) {
                bars[id] = playerBar;
				id++;
				inserted = true;
            }
        }
		if (!inserted) {
			bars[id] = playerBar;
		}
		return bars;
	}

	/**
	 * Create a single function bar
	 *
	 * @param rank        score's place, started from 1
	 * @param scoreData   score
	 * @param isSelfScore whether 'score' is from local or not, a local score would be rendered differently
	 * @return a function bar, see below comments
	 */
	private FunctionBar createFunctionBar(int rank, LeaderboardEntry entry, boolean isSelfScore) {
        IRScoreData scoreData = entry.getIrScore();
		FunctionBar irScoreBar = new FunctionBar((selector, self) -> {
            if (!entry.isLR2IR()) { return; }
            LR2GhostData ghost = LR2IRConnection.getGhostData(songData.getMd5(), entry.getLR2Id());
            if (ghost == null) {
                // whatever error caused this, it should have been reported already
                return;
            }

            int expectedNotes = songData.getNotes();
            int[] judgements = ghost.judgements;
            if (expectedNotes > judgements.length) {
                int[] padded = new int[expectedNotes];
                for (int i = 0; i < expectedNotes; ++i) {
                    padded[i] = (i < judgements.length) ? judgements[i] : 4;
                }
                judgements = padded;
            }
            else if (expectedNotes < ghost.judgements.length) {
                ImGuiNotify.error("Malformed LR2IR ghost data received.");
                return;
            }

            var target = new ScoreData();
            // properly set individual judgements? does it matter?
            target.setEgr(ghost.exScore);
            target.setNotes(judgements.length);
            target.encodeGhost(judgements);
            var play = new SongBar(songData);
            play.setRivalScore(target);

            GhostBattlePattern.forceLaneOrder(ghost.laneOrder);

            selector.selectSong(BMSPlayerMode.PLAY);
            selector.readChart(songData, play);
		}, String.format("%d. %s", rank, scoreData.player), isSelfScore ? STYLE_COURSE : STYLE_TABLE);
        irScoreBar.setScore(scoreData.convertToScoreData());
        irScoreBar.setLamp(scoreData.clear.id);
        return irScoreBar;
    }
}

// todo: like everything

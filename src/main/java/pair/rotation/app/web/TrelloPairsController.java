package pair.rotation.app.web;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pair.rotation.app.persistence.mongodb.TrelloPairsRepository;
import pair.rotation.app.trello.DayPairs;
import pair.rotation.app.trello.DayPairsHelper;
import pair.rotation.app.trello.Pair;
import pair.rotation.app.trello.PairingBoard;



@RestController
public class TrelloPairsController {
   
    private static final Logger logger = LoggerFactory.getLogger(TracksController.class);
    
    private TrelloPairsRepository repository;
	@Value("${trello.access.key}")
	private String accessKey;
	@Value("${trello.application.key}")
	private String applicationKey;
	@Value("${trello.pairing.board}")
	private String pairingBoardId;
	
    @Autowired
    public TrelloPairsController(TrelloPairsRepository repository) {
        this.repository = repository;
    }

    @RequestMapping(value = "/pairs/trello", method = RequestMethod.GET)
    public DayPairs pairs() {
    	return generatePairs(0);
    }
    
    @RequestMapping(value = "/pairs/test/trello", method = RequestMethod.GET)
    public DayPairs pairs(@RequestParam("days") int daysIntoFuture ) {
    	return generatePairs(daysIntoFuture);
    }

	private DayPairs generatePairs(int daysIntoFuture) {
		PairingBoard pairingBoardTrello = new PairingBoard(accessKey, applicationKey, pairingBoardId);
		DayPairsHelper pairsHelper = new DayPairsHelper(repository, pairingBoardTrello);
    	logger.info("Pairing board found. Syncing state now");
		pairingBoardTrello.syncTrelloBoardState();
		logger.info("Syncing state finished. Updating database state");
		pairsHelper.updateDataBaseWithTrelloContent(pairingBoardTrello.getPastPairs());
		List<DayPairs> pastPairs = repository.findAll();
		logger.info("Database state is: " + pastPairs.toString());
		Map<Pair, Integer> pairsWeight = pairsHelper.buildPairsWeight(pastPairs, pairingBoardTrello.getDevs());
		logger.info("Pairs weight is:" + pairsWeight);
		DayPairs todayPairs = pairsHelper.generateNewDayPairs(pairingBoardTrello.getTracks(), pairingBoardTrello.getDevs(), pastPairs, pairsWeight);
		logger.info("Today pairs are: " + todayPairs);
		pairsHelper.rotateSoloPairIfAny(todayPairs, pastPairs, pairsWeight);
		logger.info("After single pair rotation they are: " + todayPairs);
		pairingBoardTrello.addTodayPairsToBoard(todayPairs, daysIntoFuture);
		logger.info("Trello board has been updated");
		return todayPairs;
	}
}
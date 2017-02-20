package pair.rotation.app.trello;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.julienvey.trello.domain.Card;
import com.julienvey.trello.domain.TList;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.TrelloUrl;
import com.julienvey.trello.impl.http.RestTemplateHttpClient;


public class PairingBoard {

	private static final String CREATE_LISTS = "/lists?";
    private static final String GET_LIST_CARDS = "/lists/{listId}/cards?";
	private RestTemplateHttpClient httpClient;
	private TrelloImpl trelloImpl;
	private ArrayList<String> devs;
	private ArrayList<String> tracks;
	private ArrayList<DayPairs> pastPairs;
	private String accessKey;
	private String applicationKey;
	private String pairingBoardId;
    
	public PairingBoard(String accessKey, String applicationKey, String pairingBoardId) {
    	this.accessKey = accessKey;
		this.applicationKey = applicationKey;
		this.pairingBoardId = pairingBoardId;
		httpClient = new RestTemplateHttpClient();
    	trelloImpl = new TrelloImpl(applicationKey, accessKey, httpClient);
	}
    
	public ArrayList<String> getDevs() {
		return devs;
	}
	
	public ArrayList<String> getTracks() {
		return tracks;
	}
	
   public ArrayList<DayPairs> getPastPairs() {
	   return pastPairs;
   }

	
	public void syncTrelloBoardState() {
		devs = new ArrayList<String>();
		tracks = new ArrayList<String>();
		pastPairs = new ArrayList<DayPairs>();
		for (TList tList : getLits()) {
			String listName = tList.getName();
			System.out.println("List name is: " + listName);
			List<Card> cards = getListCards(tList.getId());
			System.out.println("Cards count is: " + cards.size());
			if ("devs".equals(listName.toLowerCase())){
				for (Card card : cards) {
				devs.addAll(card.getIdMembers());
				}
			}
			
			if ("tracks".equals(listName.toLowerCase())){
				for (Card card : cards) {
					tracks.add(card.getName());
				}
			}
			if (listName.toLowerCase().startsWith("pairing")){
				DayPairs pairs = new DayPairs();
				try {
					pairs.setDate(getDateFromCradName(tList.getName()));
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				for (Card card : cards) {
					Pair pair = new Pair();
					pair.setDevs(card.getIdMembers());
					pairs.addPair(card.getName(), pair);
					System.out.println(card.getName());
					System.out.println(card.getDesc());
				}
				pastPairs.add(pairs);
			}
		}
	}
    
	public List<TList> getLits(){
		return trelloImpl.getBoardLists(pairingBoardId);
	}
	
	public Date getDateFromCradName(String name) throws ParseException{
		String date = name.substring(name.indexOf("(") + 1, name.lastIndexOf(")"));
		return DayPairsHelper.DATE_FORMATTER.parse(date);
	}
	
	
	public List<Card> getListCards(String listId) {
		TrelloUrl getListCardsURL = TrelloUrl.createUrl(GET_LIST_CARDS);
		List<Card> listCards = Arrays.asList(httpClient.get(getListCardsURL.asString(), Card[].class, listId, applicationKey, accessKey));
		return listCards;
	}

	public void addTodayPairsToBoard(DayPairs pairs, int daysIntoFuture) {
		TList newPairingList = createNewPairingList(pairs, daysIntoFuture);
		newPairingList.setInternalTrello(trelloImpl);		
        addPairsToList(pairs, newPairingList);
	}

	private void addPairsToList(DayPairs pairs, TList newPairingList) {
		for (int i = tracks.size() - 1; i >= 0; i--) {
			if(pairs.getTracks().contains(tracks.get(i))){
				Card card = new Card();
				card.setName(tracks.get(i));
				card.setIdMembers(pairs.getPairByTrack(tracks.get(i)).getDevs());
				newPairingList.createCard(card);	
			}
		}
	}

	private TList createNewPairingList(DayPairs pairs, int daysIntoFuture) {
		TrelloUrl createListURL = TrelloUrl.createUrl(CREATE_LISTS);
		String name = "pairing(" + DayPairsHelper.DATE_FORMATTER.format(getFutureDate(pairs.getDate(), daysIntoFuture)) + ")";
		TList tList = new TList();
		tList.setName(name);
		tList.setIdBoard(pairingBoardId);
		createListURL.params();
		TList newPairingList = httpClient.postForObject(createListURL.asString(), tList, TList.class, applicationKey, accessKey);
		return newPairingList;
	}
	
	private Date getFutureDate(Date dateToStart, int daysIntoFuture) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(dateToStart);
		cal.add(Calendar.DATE, daysIntoFuture);
		return cal.getTime();
	}
}

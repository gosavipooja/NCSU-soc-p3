package actor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;

import com.fasterxml.jackson.databind.node.ObjectNode;

import actor.UserActor.QueryMessage;
import actor.UserActor.Referral;
import actor.UserActor.Refusal;
import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import controllers.HomeController;
import model.NeighborModel;
import play.libs.Json;
import referral_helper.Utils;
import scala.concurrent.Await;
import scala.concurrent.Future;
import service.Drools;

public class UserActor extends AbstractActor {
	private String name;
	private double[] expertise;
	private double[] needs;
	private List<NeighborModel> neighbors;
	private List<NeighborModel> acquaintances;
	private Drools droolsService;
	private double[] qMessage;

	private LinkedList<NeighborModel> chain;

	public UserActor() {
	}

	public UserActor(String name, double[] expertise, double[] needs, List<NeighborModel> neighbors,
			Drools droolsService) {
		super();
		this.name = name;
		this.expertise = expertise;
		this.needs = needs;
		this.neighbors = neighbors;
		this.acquaintances = neighbors;
		this.droolsService = droolsService;
		chain = new LinkedList<NeighborModel>();
	}

	public static Props props(String name, double[] expertise, double[] needs, List<NeighborModel> neighbors,
			Drools droolsService) {
		return Props.create(UserActor.class, () -> new UserActor(name, expertise, needs, neighbors, droolsService));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(DumpState.class, dumpState -> {
			ObjectNode jsonR = createJson(this);
			if (jsonR == null) {
				jsonR = Json.newObject();
				jsonR.put("status", "error");
				jsonR.put("message", "error");
			}
			System.out.println("just before sender.tell");
			sender().tell(jsonR, self());
		}).match(ActorNeeds.class, actorNeeds -> {
			sender().tell(needs, self());
		}).match(QueryMessage.class, qMessage -> {
			this.qMessage = qMessage.qMessage.clone();
			MDC.put("actr", name);
			MDC.put("sndrcv", "receive");
			droolsService.kieSession.insert(qMessage);
			droolsService.kieSession.fireAllRules();
			if (Utils.isExpertiseMatch(expertise, qMessage.qMessage)) {
				System.out.println("Query matched with own expertise: " + name);
				sender().tell(new Answer(qMessage.qMessage, Utils.genAnswer(expertise, qMessage.qMessage)), self());
			} else {
				NeighborModel nbrToRefer = getBestNeighbor(qMessage.qMessage);
				if (nbrToRefer != null) {
					if (!name.equals("default")) {
						sender().tell(new Referral(nbrToRefer.getName()), self());
					} else {
						try {
							final Timeout timeout = new Timeout(2, TimeUnit.SECONDS);
							final Future<Object> future = Patterns.ask(
									HomeController.actorMap.get(nbrToRefer.getName()), new QueryMessage(qMessage.qMessage), timeout);
							final Object response = Await.result(future, timeout.duration());
							sender().tell(response, self());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else {
					sender().tell(new Refusal(), self());
				}
			}
		}).match(Refusal.class, refuse -> {
			MDC.put("actr", name);
			MDC.put("sndrcv", "receive");
			droolsService.kieSession.insert(refuse);
			droolsService.kieSession.fireAllRules();
			sender().tell(refuse, self());
		}).match(Answer.class, ans -> {
			MDC.put("actr", name);
			MDC.put("sndrcv", "receive");
			droolsService.kieSession.insert(ans);
			droolsService.kieSession.fireAllRules();
			// update expertise and neighbors & choose best acquaintances as neighbor
			updateExpSoc(ans);
			sender().tell(ans, self());
		}).match(Referral.class, ref -> {
			MDC.put("actr", name);
			MDC.put("sndrcv", "receive");
			droolsService.kieSession.insert(ref);
			droolsService.kieSession.fireAllRules();
			if (!name.equals("default")) {
				try {
					Timeout time = new Timeout(5, TimeUnit.SECONDS);
					final Future<Object> obj = Patterns.ask(HomeController.actorMap.get(ref.referralName),
							new QueryMessage(qMessage), time);
					final Object result = Await.result(obj, time.duration());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				sender().tell(ref, self());
			}
		}).build();
	}

	private void updateExpSoc(Answer ans) {
		for (NeighborModel n : chain) {
			if (!acquaintances.contains(n)) {
				acquaintances.add(n);
			}
		}
		NeighborModel answerer = chain.getLast();
		Utils.updateExpertise(ans.queryMessage, ans.answerReply, answerer.getExpertise());
		for (NeighborModel aq : acquaintances) {
			if (aq.getName().equals(answerer.getName())) {
				aq = answerer;
			}
		}
		for (NeighborModel n : chain) {
			if (!n.getName().equals(answerer.getName()) && !n.getName().equals(name)) {
				int d2t = chain.size() - chain.indexOf(n) - 1;
				Utils.updateSociability(ans.queryMessage, ans.answerReply, d2t, n.getSociability());
				for (NeighborModel aq : acquaintances) {
					if (aq.getName().equals(n.getName())) {
						aq = n;
					}
				}
			}
		}
		int maxN = Utils.getMaxNumOfNeighbors();
		TreeMap<Double, NeighborModel> highestAqs = new TreeMap<Double, NeighborModel>();
		double[] defQ = { 1, 1, 1, 1 };
		for (NeighborModel aq : acquaintances) {
			double w = Utils.getWeightOfSociability();
			double f = w * innerProduct(defQ, aq.getSociability()) + (1 - w) * innerProduct(defQ, aq.getExpertise());
			highestAqs.put(f, aq);
		}
		neighbors.clear();
		for (int i = 0; i < maxN; i++) {
			NeighborModel n = highestAqs.get(highestAqs.lastKey());
			highestAqs.remove(n);
			neighbors.add(n);
		}
	}

	private NeighborModel getBestNeighbor(double[] query) {
		SortedMap<Double, NeighborModel> bestNeighbor = new TreeMap<Double, NeighborModel>();
		for (NeighborModel aq : neighbors) {
			if (Utils.isExpertiseMatch(aq.getExpertise(), query)
					|| Utils.isExpertiseMatch(aq.getSociability(), query)) {
				double w = Utils.getWeightOfSociability();
				double f = w * innerProduct(query, aq.getSociability())
						+ (1 - w) * innerProduct(query, aq.getExpertise());
				bestNeighbor.put(f, aq);
			}
		}
		if (bestNeighbor == null || bestNeighbor.size() == 0) {
			return null;
		} else {
			return bestNeighbor.get(bestNeighbor.lastKey());
		}
	}

	private double innerProduct(double[] a, double[] b) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			sum += a[i] * b[i];
		}
		return sum;
	}

	public ObjectNode createJson(UserActor act) {
		System.out.println("Inside create json");
		ObjectNode jsonR = Json.newObject();
		try {
			jsonR.put("status", "success");
			if (act.neighbors == null || act.neighbors.size() == 0) {
				jsonR.put("neighbors", "[]");
			} else {
				jsonR.set("neighbors", play.libs.Json.toJson(neighbors));
			}
			if (act.acquaintances == null || act.acquaintances.size() == 0) {
				jsonR.put("acquaintances", "[]");
			} else {
				jsonR.set("acquaintances", play.libs.Json.toJson(acquaintances));
			}

		} catch (Exception e) {
			System.out.println("Null json");
			System.out.println(e.getMessage());
			return null;
		}
		return jsonR;
	}

	/*
	 * Following are the message types and their classes
	 */

	static public class QueryMessage {
		public double[] qMessage;

		public QueryMessage(double[] qMessage) {
			super();
			this.qMessage = qMessage;
		}

	}

	static public class DumpState {

		public DumpState() {
			super();
		}

	}

	static public class ActorNeeds {
		public ActorNeeds() {
			super();
		}
	}

	static public class Answer {
		public double[] queryMessage;
		public double[] answerReply;

		public Answer(double[] queryMessage, double[] answerReply) {
			this.queryMessage = queryMessage;
			this.answerReply = answerReply;
		}
	}

	static public class Refusal {

		public Refusal() {
			super();
		}
	}

	static public class Referral {

		public String referralName;

		public Referral(String referralName) {
			super();
			this.referralName = referralName;
		}
	}

}
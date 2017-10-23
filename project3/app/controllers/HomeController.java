package controllers;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import actor.UserActor;
import actor.UserActor.ActorNeeds;
import actor.UserActor.Answer;
import actor.UserActor.DumpState;
import actor.UserActor.QueryMessage;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import model.NeighborModel;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import referral_helper.QueryGenerator;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.Future;
import service.Drools;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
@Singleton
public class HomeController extends Controller {

	final ActorSystem system;
	final Drools droolsService;
	public static Map<String, ActorRef> actorMap = new HashMap<String, ActorRef>();

	@Inject
	public HomeController(ActorSystem system, Drools droolsService) {
		this.system = system;
		this.droolsService = droolsService;
	}

	public Result index() {
		return ok(views.html.index.render());
	}

	public Result getAnswerFromQuery(String actorName, String queryVec) {
		System.out.println("Inside get Answer From Query");
		ActorRef actRef = actorMap.get(actorName);
		double[] qq = new double[4];
		int i = 0;
		for (String q : queryVec.split(",")) {
			qq[i++] = Double.valueOf(q);
		}
		try {
			final Timeout timeout = new Timeout(15, TimeUnit.SECONDS);
			final Future<Object> future = Patterns.ask(actRef, new QueryMessage(qq), timeout);
			final Object answer = (Object) Await.result(future, timeout.duration());
			System.out.println("Got query answer in Controller");
			if (answer != null) {
				if (answer instanceof Answer) {
					System.out.println("Answer is answer");
					Answer a =(Answer) answer;
					ObjectNode result = Json.newObject();
					result.put("status","success");
					result.set("answer", play.libs.Json.toJson(a.answerReply));
					return ok(result);
				} else {
					System.out.println("Answer is refusal");
					ObjectNode result = Json.newObject();
					result.put("status","error");
					result.put("message", "No Reply");
					return ok(result);
				}
			} else {
				ObjectNode result = Json.newObject();
				result.put("status","error");
				result.put("message", "Could not find a match for query.");
				return ok(result);
			}
		} catch (Exception e) {
			e.printStackTrace();
			ObjectNode result = Json.newObject();
			result.put("status","error");
			result.put("message", "Error while executing Query.");
			return ok(result);
		}
	}

	public CompletionStage<Result> dumpAllStates(String actorName) {
		System.out.println("Inside dump all states");
		ActorRef dumpActor = actorMap.get(actorName);
		System.out.println("For actor " + actorName);
		if (dumpActor != null) {
			System.out.println("Dumped Actor is not null");
			return FutureConverters.toJava(Patterns.ask(dumpActor, new DumpState(), 1000))
					.thenApply(res -> ok((ObjectNode) res));
		} else {
			System.out.println("Dumped Actor is null");
			return CompletableFuture.completedFuture(null);

		}
	}

	public Result getMessages() {
		String myFile = "";
		System.out.println("Inside Get Messages");
		try {
			FileInputStream fstream = new FileInputStream("./logs/application.log");
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				myFile = myFile + strLine + "\n";
			}
			fstream.close();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
		return ok(myFile);
	}

	public Result postInputGraph() {
		System.out.println("Inside Post Input Graph");

		ObjectNode jsonR = Json.newObject();
		JsonNode jNode = request().body().asJson();
		System.out.println("Input received : " + jNode);
		if (jNode == null) {
			System.out.println("Your JSON Graph is null");
			jsonR.put("status", "error");
			jsonR.put("message", "Your input graph is null");
			return ok(jsonR);
		} else {
			for (JsonNode jIter : jNode) {
				parseInputGraph(jIter);
			}
			for (Map.Entry<String, ActorRef> entry : actorMap.entrySet()) {
				System.out.println("For actor : " + entry.getKey() + " Value : " + entry.getValue());

			}
			jsonR.put("status", "success");
			return ok(jsonR);
		}

	}

	public void parseInputGraph(JsonNode jIter) {
		// ActorRef actor = system.actorOf(UserActor.props(name, expertise, needs,
		// neighbors))
		List<NeighborModel> myNeighbors = new ArrayList<NeighborModel>();
		double[] aExpertise = new double[4];
		double[] aNeeds = new double[4];
		int aIndex = 0;
		// Only Neighbors **********************************
		JsonNode neighborNode = jIter.get("neighbors");
		if (neighborNode != null) {
			for (JsonNode jNode : neighborNode) {
				
				System.out.println("***************************");
				System.out.println(jNode.asText());
				System.out.println("***************************");
				
				double[] expertise = new double[4];
				double[] sociability = new double[4];
				int i = 0;
				NeighborModel nObj = new NeighborModel();
				nObj.setName(jNode.get("name").asText());
				System.out.println("???????? Name = "+jNode.get("name"));
				JsonNode nExpertise = jNode.get("expertise");
				if (nExpertise != null) {
					for (JsonNode exp : nExpertise) {
						expertise[i++] = exp.asDouble();
					}
					System.out.println("???????? expertise = "+jNode.get("expertise"));
					System.out.println("***************************");
					System.out.println("???????? expertise = "+expertise);

					nObj.setExpertise(expertise);
				} else {
					System.out.println("expertise missing");
				}
				i = 0;
				JsonNode nSociability = jNode.get("sociability");
				if (nSociability != null) {
					for (JsonNode soc : nSociability) {
						expertise[i++] = soc.asDouble();
					}
					nObj.setSociability(sociability);
				} else {
					System.out.println("no sociability");
				}
				myNeighbors.add(nObj);
			}
		} else {
			System.out.println("no neighbors");
		}
		// ********************************************************
		String actorName = jIter.get("name").asText();
		// ********************************************************
		JsonNode expertise = jIter.get("expertise");
		if (expertise != null) {
			for (JsonNode exp : expertise) {
				aExpertise[aIndex++] = exp.asDouble();

			}
		} else {
			System.out.println("no expertise chu is not an expert");
		}
		// ********************************************************
		aIndex = 0;
		JsonNode needs = jIter.get("needs");
		if (needs != null) {
			for (JsonNode n : needs) {
				aNeeds[aIndex++] = n.asDouble();
			}
		} else {
			System.out.println("no needs");
		}
		ActorRef actor = system.actorOf(UserActor.props(actorName, aExpertise, aNeeds, myNeighbors, droolsService));
		actorMap.put(actorName, actor);
		runAllQueries();
	}

	public void runAllQueries() {
		for (Map.Entry<String, ActorRef> entry : actorMap.entrySet()) {
			String actorName = entry.getKey();
			Timeout time = new Timeout(5, TimeUnit.SECONDS);
			try {
				final Future<Object> obj = Patterns.ask(entry.getValue(), new ActorNeeds(), time);
				final double[] result = (double[]) Await.result(obj, time.duration());
				for (int i = 0; i < 25; i++) {
					double[] queryVal = QueryGenerator.getInstance().genQuery(actorName, result);
					System.out.println("\n Query Returned for actor: " + actorName + " i = " + i);
					FutureConverters.toJava(Patterns.ask(entry.getValue(), new QueryMessage(queryVal), 1000))
							.thenApply(res -> (String) res);

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
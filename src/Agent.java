import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JOptionPane;

public class Agent extends jade.core.Agent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2139586581094493078L;
	
	private String mainTeam = null;
	private Set<String> otherTeams = new HashSet<String>();
	private Integer meetingStart=8; //ADOPT VARIABLE
	
	private Map<String, AID>  teamMasters = new HashMap<String, AID>();
	private Map<AID, Integer> costs = new HashMap<AID, Integer>();
	private Map<AID, Integer> meetingLength = new HashMap<AID, Integer>();
	private Map<AID, Integer> priority = new HashMap<AID, Integer>();
	
	private AID parent;
	private AID child;
	
	private enum State {
		INIT, MEETING_GATHERING, COST_PROPAGATION, COST_GATHERING, BUILD_DFS, ADOPT;
	}
	
	@Override
	protected void setup() {
		super.setup();
		
		// Define the team I supervise
		String res0 = JOptionPane.showInputDialog("The name of the team you supervise:");
		if (res0==null || res0.isEmpty())
			mainTeam = null;
		else
			mainTeam = res0.toLowerCase();
		
		if(mainTeam != null){
			System.out.println("["+getLocalName()+"] MAIN TEAM = "+mainTeam);
		}
		
		// Define the team I belong to 
		String res1 = JOptionPane.showInputDialog("The names of the teams you belong to (comma-separated):");
		while (mainTeam == null && (res1==null || res1.isEmpty())){
			JOptionPane.showMessageDialog(null,
				    "You must belong to a team at least!",
				    "ERROR",
				    JOptionPane.ERROR_MESSAGE);
			res1 = JOptionPane.showInputDialog("The names of the teams you belong to (comma-separated):");
		}
		if (res1!=null && !res1.isEmpty())
			for(String team: res1.split(","))
				otherTeams.add(team.toLowerCase());
		
		
		StringBuffer otherTeamsPrint = new StringBuffer();
		for (String team : otherTeams) {
			if(otherTeamsPrint.length() != 0){
				otherTeamsPrint.append(",");
			}
			otherTeamsPrint.append(team);
		}
		System.out.println("["+getLocalName()+"] OTHER TEAMS = "+otherTeamsPrint.toString());
		
		// Scheduling a meeting
		if (mainTeam != null){
			String res2 = JOptionPane.showInputDialog("How long will your meeting last?");
			if (res2 == null || res2.isEmpty())
				meetingLength.put(getAID(), null);
			else{
				int length = Integer.valueOf(res2);
				if (length < 1 || length > 10)
					meetingLength.put(getAID(), null);
				else
					meetingLength.put(getAID(), length);
			}
		}
		
		if(meetingLength!=null){
			System.out.println("["+getLocalName()+"] MEETING LENGTH = "+meetingLength.get(getAID()));
		}

		
		//Advertise
		DFAgentDescription dfd = new DFAgentDescription();
	    dfd.setName(getAID());
	    ServiceDescription sd = new ServiceDescription();
	    sd.setType("scheduling");
	    sd.setName(mainTeam != null ? mainTeam : "[none]");
	    dfd.addServices(sd);
	    try {
	      DFService.register(this, dfd);
	    }
	    catch (FIPAException fe) {
	      fe.printStackTrace();
	    }
	    
		//Main Behavior
		addBehaviour(new Behaviour() {
			/**
			 * 
			 */
			private static final long serialVersionUID = -5015340923249652578L;
			
			private State state = State.INIT;
			private Set<AID> others = new HashSet<AID>();
			private Integer rand = (int)Math.floor(Math.random()*100);
			
			@Override
			public void action() {
				System.out.println("["+myAgent.getLocalName()+"] STATE = " + state.toString());
				
				switch (state) {
				case INIT:
					MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchContent("START"));
					blockingReceive(mt);
					System.out.println("["+getLocalName()+"] START MESSAGE RECEIVED");
					if(mainTeam != null){
						teamMasters.put(mainTeam, myAgent.getAID());
					}
					DFAgentDescription template = new DFAgentDescription();
			        ServiceDescription sd = new ServiceDescription();
			        sd.setType("scheduling");
			        template.addServices(sd);
			        try {
			        	DFAgentDescription[] results = DFService.search(myAgent, template);
			        	for(DFAgentDescription result : results){
			        		if(!result.getName().equals(myAgent.getAID())){
			        			others.add(result.getName());
			        		}
			        	}
			        	System.out.println("["+getLocalName()+"] FOUND OTHER AGENTS = " + others.toString());
			        	ACLMessage msg = new ACLMessage(
			        			(mainTeam != null && meetingLength.get(myAgent.getAID())!=null)?
			        					ACLMessage.CONFIRM:
			        					ACLMessage.CANCEL);
			        	for(AID agent: others){
			        		msg.addReceiver(agent);
			        	}
			        	msg.setConversationId(State.MEETING_GATHERING.toString());
			        	if(mainTeam != null && meetingLength.get(myAgent.getAID())!=null) {
			        		msg.setContent(mainTeam + ":" + meetingLength.get(myAgent.getAID()).toString());
			        	} else if (mainTeam != null) {
			        		msg.setContent(mainTeam);
			        	}
			        	System.out.println("["+getLocalName()+"] SENDING MEETING MESSAGES");
			        	myAgent.send(msg);
			        	state = State.MEETING_GATHERING;
			        	
			        	ACLMessage ordMsg = new ACLMessage(ACLMessage.INFORM);
			        	for(DFAgentDescription result : results){
			        		ordMsg.addReceiver(result.getName());
			        	}
			        	ordMsg.setConversationId(State.BUILD_DFS.toString());
			        	ordMsg.setContent(rand.toString());
			        	myAgent.send(ordMsg);
			        } catch (FIPAException fe) {
			        	fe.printStackTrace();
			        }
					break;
				case MEETING_GATHERING:
					if(others.isEmpty()){
						state = State.COST_PROPAGATION;
						break;
					}
					
					ACLMessage msg = myAgent.receive(
									MessageTemplate.MatchConversationId(State.MEETING_GATHERING.toString()));
					if(msg!=null){
						switch(msg.getPerformative()){
						case ACLMessage.CONFIRM:
							String[] content = msg.getContent().split(":");
							String key = content[0];
							Integer value = Integer.valueOf(content[1]);
							teamMasters.put(key, msg.getSender());
							meetingLength.put(msg.getSender(), value);
							break;
						case ACLMessage.CANCEL:
							if(msg.getContent() != null && !msg.getContent().isEmpty()){
								teamMasters.put(msg.getContent(), msg.getSender());
								otherTeams.remove(msg.getContent());
							}
							break;
						}
						others.remove(msg.getSender());
					} else {
						block();
					}
					break;
				case COST_PROPAGATION:
					if(!otherTeams.isEmpty()){
						if(mainTeam != null && costs.get(myAgent.getAID()) != null) {
							for(String otherTeam: otherTeams){
								DFAgentDescription template1 = new DFAgentDescription();
						        ServiceDescription sd1 = new ServiceDescription();
						        sd1.setType("scheduling");
						        sd1.setName(otherTeam);
						        template1.addServices(sd1);
						        try {
						        	DFAgentDescription[] results = DFService.search(myAgent, template1);
						        	if(results.length != 0){
						        		ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
						        		msg1.addReceiver(results[0].getName());
						        		msg1.setConversationId(State.COST_PROPAGATION.toString());
						        		msg1.setContent(mainTeam + ":3");
						        		myAgent.send(msg1);
						        		costs.put(results[0].getName(), costs.containsKey(results[0].getName())?costs.get(results[0].getName())+3:3);
						        	}
						        } catch (FIPAException fe) {
						        	fe.printStackTrace();
						        }
							}
						}
					}
						
					if(otherTeams.size() > 1){
						String[] otAll = new String[otherTeams.size()];
						otAll = otherTeams.toArray(otAll);
						for(int i=0; i<otAll.length; i++){
							String otA = otAll[i];
							for(int j=0; j<otAll.length; j++){
								if(i!=j){
									DFAgentDescription template2 = new DFAgentDescription();
							        ServiceDescription sd2 = new ServiceDescription();
							        sd2.setType("scheduling");
							        sd2.setName(otAll[j]);
							        template2.addServices(sd2);
							        try {
							        	DFAgentDescription[] results = DFService.search(myAgent, template2);
							        	if(results.length != 0){
							        		ACLMessage msg2 = new ACLMessage(ACLMessage.INFORM);
							        		msg2.addReceiver(results[0].getName());
							        		msg2.setConversationId(State.COST_PROPAGATION.toString());
							        		msg2.setContent(otA + ":1");
							        		myAgent.send(msg2);
							        	}
							        } catch (FIPAException fe) {
							        	fe.printStackTrace();
							        }
								}
							}
						}
					}
					others = new HashSet<AID>();
					DFAgentDescription template3 = new DFAgentDescription();
			        ServiceDescription sd3 = new ServiceDescription();
			        sd3.setType("scheduling");
			        template3.addServices(sd3);
			        try {
			        	DFAgentDescription[] results = DFService.search(myAgent, template3);
			        	for(DFAgentDescription result : results){
			        		if(!result.getName().equals(myAgent.getAID())){
			        			others.add(result.getName());
			        		}
			        	}
			        	ACLMessage msg3 = new ACLMessage(ACLMessage.CONFIRM);
			        	for(AID agent: others){
			        		msg3.addReceiver(agent);
			        	}
			        	msg3.setConversationId(State.COST_GATHERING.toString());
			        	myAgent.send(msg3);
						state = State.COST_GATHERING;
			        } catch (FIPAException fe) {
			        	fe.printStackTrace();
			        }
					break;
				case COST_GATHERING:
					if(others.isEmpty()){
						state = State.BUILD_DFS;
						break;
					}
					
					ACLMessage msg4 = myAgent.receive(
							MessageTemplate.and(
									MessageTemplate.MatchPerformative(ACLMessage.CONFIRM), 
									MessageTemplate.MatchConversationId(State.COST_GATHERING.toString())));
					if (msg4 != null){
						others.remove(msg4.getSender());
					} else {
						block();
					}
					break;
				case BUILD_DFS:
					List<Integer> priorityArr = new ArrayList<Integer>();
					priorityArr.addAll(priority.values());
					Collections.sort(priorityArr);
					Collections.reverse(priorityArr);
					
					AID[] agentOrd = new AID[priority.keySet().size()];
					
					int j=0;
					for(int i=0; i<priorityArr.size(); i++){
						int curr=priorityArr.get(i);
						for(Entry<AID, Integer> val : priority.entrySet()){
							if(val.getValue().equals(curr)){
								agentOrd[j] = val.getKey();
								j++;
							}
						}
					}
					
					for(int i=0; i<agentOrd.length; i++){
						if(agentOrd[i].equals(myAgent.getAID())){
							if(i==0){
								Agent.this.parent = null;
								Agent.this.child = agentOrd[i+1];
							} else if(i==agentOrd.length-1) {
								Agent.this.parent = agentOrd[i-1];
								Agent.this.child = null;
							} else {
								Agent.this.parent = agentOrd[i-1];
								Agent.this.child = agentOrd[i+1];
							}
						}
					}
					state = State.ADOPT;
					break;
				case ADOPT:
					//TODO implement ADOPT
					break;
				}
			}
			
			@Override
			public boolean done() {
				// TODO Auto-generated method stub
				return false;
			}
		});
		
		//Responder for cost building
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.INFORM), 
								MessageTemplate.MatchConversationId(State.COST_PROPAGATION.toString())));
				if (msg != null){
					String[] content = msg.getContent().split(":");
					String key = content[0];
					Integer value = Integer.valueOf(content[1]);
					
					DFAgentDescription template = new DFAgentDescription();
			        ServiceDescription sd = new ServiceDescription();
			        sd.setType("scheduling");
			        sd.setName(key);
			        template.addServices(sd);
			        try {
			        	DFAgentDescription[] results = DFService.search(myAgent, template);
			        	if(results.length != 0){
			        		costs.put(results[0].getName(), costs.containsKey(results[0].getName())?costs.get(results[0].getName())+value:value);
			        	}
			        } catch (FIPAException fe) {
			        	fe.printStackTrace();
			        }
				} else {
					block();
				}
			}
		});
		
		//Ordering messages
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.INFORM), 
								MessageTemplate.MatchConversationId(State.BUILD_DFS.toString())));
				if (msg != null){
					priority.put(msg.getSender(), Integer.valueOf(msg.getContent()));
				} else {
					block();
				}
			}
		});
	}
	
	@Override
	protected void takeDown() {
		// TODO Auto-generated method stub
		super.takeDown();
	}
}

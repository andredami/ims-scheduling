import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
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
	
	private Double threshold = 0.0;
	private Map<AID, Integer> currentContext = new HashMap<AID, Integer>();
	private Map<Integer, Double> lb = new HashMap<Integer, Double>();
	private Map<Integer, Double> ub = new HashMap<Integer, Double>();
	private Map<Integer, Double> t  = new HashMap<Integer, Double>();
	private Map<Integer, Map<AID, Integer>> context = new HashMap<Integer, Map<AID,Integer>>();
	
	private AID parent;
	private AID child;
	
	private Boolean terminate = false;
	
	private enum State {
		INIT, MEETING_GATHERING, COST_PROPAGATION, COST_GATHERING, BUILD_DFS, ADOPT_INIT, ADOPT;
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
						if(mainTeam != null) {
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
					state = State.ADOPT_INIT;
					break;
				case ADOPT_INIT:
					for(int i=8; i<18; i++){
						lb.put(i, 0.0);
						ub.put(i, Double.POSITIVE_INFINITY);
						t.put(i, 0.0);
						context.put(i, new HashMap<AID, Integer>());
					}
					backTrack();
					state = State.ADOPT;
					break;
				case ADOPT:
					break;
				}
			}
			
			@Override
			public boolean done() {
				return (state.equals(State.ADOPT));
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
		
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE), 
								MessageTemplate.MatchConversationId("VALUE")));
				if (msg != null){
					if(!terminate){
						currentContext.put(msg.getSender(), Integer.valueOf(msg.getContent()));
						checkIncompatiblity();
						double tmpUB = UB();
						double tmpLB = LB();
						if(threshold>tmpUB){
							threshold = tmpUB;
						} else if(threshold<tmpLB) {
							threshold = tmpLB;
						}
						backTrack();
					}
				} else {
					block();
				}
			}
		});
		
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE), 
								MessageTemplate.MatchConversationId("COST")));
				if (msg != null){
					String[] contents = msg.getContent().split(";",4);
					String[] contextFields = contents[3].split(",");
					Map<AID,Integer> ctxMsg = new HashMap<AID,Integer>();
					for(String contextField:contextFields){
						if(contextField==null || contextField.isEmpty()){
							break;
						}
						String[] parts = contextField.split(":");
						try {
							AID aid = (AID) fromString(parts[0]);
							Integer val = Integer.valueOf(parts[1]);
							ctxMsg.put(aid, val);
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Double lbMsg = Double.valueOf(contents[1]);
					Double ubMsg = Double.valueOf(contents[2]);
					Integer dCtx = ctxMsg.get(myAgent.getAID());
					ctxMsg.remove(myAgent.getAID());
					if(!terminate){
						for(Entry<AID, Integer> c:ctxMsg.entrySet()){
							if(!costs.containsKey(c.getKey())){
								currentContext.put(c.getKey(), c.getValue());
							}
						}
						checkIncompatiblity();
					}
					if(isCompatibleWithCurrentContext(ctxMsg)){
						lb.put(dCtx, lbMsg);
						ub.put(dCtx, ubMsg);
						context.put(dCtx, ctxMsg);
					}
					double tmpUB = UB();
					double tmpLB = LB();
					if(threshold>tmpUB){
						threshold = tmpUB;
					} else if(threshold<tmpLB) {
						threshold = tmpLB;
					}
					checkChildThresholdInvariant();
					backTrack();
				} else {
					block();
				}
			}
		});
		
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE), 
								MessageTemplate.MatchConversationId("TERMINATE")));
				if (msg != null){
					terminate = true;
					backTrack();
				} else {
					block();
				}
			}
		});
		
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(
						MessageTemplate.and(
								MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE), 
								MessageTemplate.MatchConversationId("THRESHOLD")));
				if (msg != null){
					String[] contents = msg.getContent().split(";",2);
					String[] contextFields = contents[1].split(",");
					Map<AID,Integer> context = new HashMap<AID,Integer>();
					for(String contextField:contextFields){
						if(contextField==null || contextField.isEmpty()){
							break;
						}
						String[] parts = contextField.split(":");
						try {
							AID aid = (AID) fromString(parts[0]);
							Integer val = Integer.valueOf(parts[1]);
							context.put(aid, val);
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Double msgT = Double.valueOf(contents[0]);
					if(isCompatibleWithCurrentContext(context)){
						threshold = msgT;
						double tmpUB = UB();
						double tmpLB = LB();
						if(threshold>tmpUB){
							threshold = tmpUB;
						} else if(threshold<tmpLB) {
							threshold = tmpLB;
						}
						backTrack();
					}
				} else {
					block();
				}
			}
		});
	}
	
	private boolean isCompatibleWithCurrentContext(Map<AID,Integer> ctx){
		for(Entry<AID, Integer> entry: ctx.entrySet()){
			Integer value = currentContext.get(entry.getKey());
			if(value!=null && !value.equals(entry.getValue()))
				return false;
		}
		
		return true;
	}
	
	private void backTrack(){
		if(threshold.equals(UB())){
			UBUpdate();
		} else if(LBnow() > threshold) {
			LB();
		}
		ACLMessage value = new ACLMessage(ACLMessage.PROPAGATE);
		for(Entry<AID,Integer> e: costs.entrySet()){
			if(priority.get(getAID())>priority.get(e.getKey())){
				value.addReceiver(e.getKey());
			}
		}
		value.setContent(meetingStart.toString());
		value.setConversationId("VALUE");
		send(value);
		
		mantainAllocationInvariant();
		
		if(threshold.equals(UB())){
			if(terminate || parent == null){
				ACLMessage termMsg = new ACLMessage(ACLMessage.PROPAGATE);
				termMsg.setConversationId("TERMINATE");
				termMsg.addReceiver(child);
				send(termMsg);
				terminate = true;
			}
		}
		if(!terminate && parent != null){
			ACLMessage cost = new ACLMessage(ACLMessage.PROPAGATE);
			cost.addReceiver(parent);
			StringBuilder ctxSerial = new StringBuilder();
			for(Entry<AID, Integer> ctx:currentContext.entrySet()){
				if(ctxSerial.length()!=0){
					ctxSerial.append(",");
				}
				try {
					ctxSerial.append(Agent.toString(ctx.getKey()));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				ctxSerial.append(":"+Integer.valueOf(ctx.getValue()));
			}
			cost.setContent(meetingStart.toString()+";"+LB().toString()+";"+UB().toString()+";"+ctxSerial.toString());
			cost.setConversationId("COST");
			send(cost);
		}
		if(terminate){
			//TODO Termination signalation
		}
	}

	private void checkIncompatiblity(){
		for(int i=8; i<18; i++){
			Map<AID, Integer> varContext = context.get(i);
			if(varContext == null){
				break;
			}
			for(Entry<AID, Integer> e: varContext.entrySet()){
				if(currentContext.containsKey(e.getKey()) && currentContext.get(e.getKey())!=e.getValue()){
					//Context Invalidate
					lb.put(i, 0.0);
					ub.put(i, Double.POSITIVE_INFINITY);
					t.put(i, 0.0);
					context.put(i, new HashMap<AID, Integer>());
				}
			}
		}
	}

	private void mantainAllocationInvariant(){
		if(threshold>Tnow()){
			t.put(meetingStart, t.get(meetingStart)+1);
		} else if(threshold<Tnow()){
			t.put(meetingStart, t.get(meetingStart)-1);
		}
		ACLMessage msg = new ACLMessage(ACLMessage.PROPAGATE);
		msg.setConversationId("THRESHOLD");
		msg.addReceiver(child);
		StringBuilder ctxSerial = new StringBuilder();
		for(Entry<AID, Integer> ctx:currentContext.entrySet()){
			if(ctxSerial.length()!=0){
				ctxSerial.append(",");
			}
			try {
				ctxSerial.append(Agent.toString(ctx.getKey()));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			ctxSerial.append(":"+Integer.valueOf(ctx.getValue()));
		}
		msg.setContent(t.get(meetingStart)+";"+ctxSerial.toString());
		send(msg);
	}

	private void checkChildThresholdInvariant(){
		for(int i=8; i<18; i++){
			if(lb.get(i) != null && t.get(i) !=null && lb.get(i)>t.get(i)){
				t.put(i,t.get(i)+1);
			}
			if(ub.get(i) != null && t.get(i) !=null && ub.get(i)<t.get(i)){
				t.put(i,t.get(i)-1);
			}
		}
	}
	
	private Double Tnow(){
		double count = 0;
		for(Entry<AID, Integer> e: currentContext.entrySet()){
			count+=constraint(meetingStart, meetingLength.get(getAID()), e.getValue(), meetingLength.get(e.getKey()), costs.get(e.getKey()));
		}
		if(child!=null){
			count+=t.get(meetingStart);
		}
		return count;
	}
	
	private Double LB(){
		Double[] array = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0};
		for(int i=8; i<18; i++){
			for(Entry<AID, Integer> e: currentContext.entrySet()){
				array[i-8]+=constraint(i, meetingLength.get(getAID()), e.getValue(), meetingLength.get(e.getKey()), costs.get(e.getKey()));
			}
		}
		if(child!=null){
			for(int i=8; i<18; i++){
				array[i-8]+=lb.get(i);
			}
		}
		Double min= array[0];
		int d = 0;
		for(int i=1; i<10; i++){
			if(array[i]<min){
				min = array[i];
				d = i;
			}
		}
		if (threshold<min){
			threshold=min;
		}
		meetingStart = d + 8;
		return min;
	}

	private Double LBnow(){
		double count = 0;
		for(Entry<AID, Integer> e: currentContext.entrySet()){
			count+=constraint(meetingStart, meetingLength.get(getAID()), e.getValue(), meetingLength.get(e.getKey()), costs.get(e.getKey()));
		}
		if(child!=null){
			count+=lb.get(meetingStart);
		}
		return count;
	}
	
	private Double UB(){
		Double[] array = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0};
		for(int i=8; i<18; i++){
			for(Entry<AID, Integer> e: currentContext.entrySet()){
				array[i-8]+=constraint(i, meetingLength.get(getAID()), e.getValue(), meetingLength.get(e.getKey()), costs.get(e.getKey()));
			}
		}
		if(child!=null){
			for(int i=8; i<18; i++){
				array[i-8]+=ub.get(i);
			}
		}
		Double min= array[0];
		for(int i=1; i<10; i++){
			min= array[i]<min?array[i]:min;
		}
		if (threshold>min){
			threshold=min;
		}
		return min;
	}
	
	private void UBUpdate(){
		Double[] array = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0};
		for(int i=8; i<18; i++){
			for(Entry<AID, Integer> e: currentContext.entrySet()){
				array[i-8]+=constraint(i, meetingLength.get(getAID()), e.getValue(), meetingLength.get(e.getKey()), costs.get(e.getKey()));
			}
		}
		if(child!=null){
			for(int i=8; i<18; i++){
				array[i-8]+=ub.get(i);
			}
		}
		Double min= array[0];
		int d = 0;
		for(int i=1; i<10; i++){
			if(array[i]<min){
				min = array[i];
				d = i;
			}
		}
		meetingStart = d + 8;
	}
	
	private Double constraint(Integer x, Integer lx, Integer y, Integer ly, Integer cost){
		if(x>=y+ly || x+lx<=y){
			return 0.0;
		} else {
			return Double.valueOf(cost);
		}
	}
	
	private static String toString( Serializable o ) throws IOException {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream( baos );
	    oos.writeObject( o );
	    oos.close();
	    return Base64.getEncoder().encodeToString(baos.toByteArray()); 
	}
	
	   private static Object fromString( String s ) throws IOException ,
       ClassNotFoundException {
		   byte [] data = Base64.getDecoder().decode( s );
		   ObjectInputStream ois = new ObjectInputStream( 
				   new ByteArrayInputStream(  data ) );
		   Object o  = ois.readObject();
		   ois.close();
		   return o;
	   }
}

import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;

public class Agent extends jade.core.Agent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2139586581094493078L;
	
	private String mainTeam = null;
	private Set<String> otherTeams = new HashSet<String>(); 
	private Integer meetingLength;
	private Integer meetingStart=8;
	
	private Set<AID> neighbors = new HashSet<AID>();
	
	private enum State {
		BUILD_NEIGHBOURS_PREAMBLE, BUILD_NEIGHBOURS, BUILD_DFS_PREAMBLE;
	}
	
	@Override
	protected void setup() {
		// TODO Auto-generated method stub
		super.setup();
		
		// Define the team I supervise
		String res0 = JOptionPane.showInputDialog("The name of the team you supervise:");
		if (res0==null || res0.isEmpty())
			mainTeam = null;
		else
			mainTeam = res0.toLowerCase();
		
		if(mainTeam != null){
			System.out.println("["+getName()+"] MAIN TEAM = "+mainTeam);
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
		System.out.println("["+getName()+"] OTHER TEAMS = "+otherTeamsPrint.toString());
		
		// Scheduling a meeting
		if (mainTeam != null){
			String res2 = JOptionPane.showInputDialog("How long will your meeting last?");
			if (res2 == null || res2.isEmpty())
				meetingLength = null;
			else{
				int length = Integer.valueOf(res2);
				if (length < 1 || length > 10)
					meetingLength = null;
				else
					meetingLength = length;
			}
		}
		if(meetingLength!=null){
			System.out.println("["+getName()+"] MEETING LENGTH = "+meetingLength.toString());
		}

		
		//Advertise
		DFAgentDescription dfd = new DFAgentDescription();
	    dfd.setName(getAID());
	    ServiceDescription sd = new ServiceDescription();
	    sd.setType("scheduling");
	    sd.setName("JADE-meeting-scheduling");
	    dfd.addServices(sd);
	    try {
	      DFService.register(this, dfd);
	    }
	    catch (FIPAException fe) {
	      fe.printStackTrace();
	    }
		
	    // Wait For Start Signal
	    MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchContent("START"));
	    blockingReceive(mt);
		
		//Main Behavior
		addBehaviour(new Behaviour() {
			/**
			 * 
			 */
			private static final long serialVersionUID = -5015340923249652578L;

			private State state = State.BUILD_NEIGHBOURS_PREAMBLE;
			
			private Set<AID> others;
			
			private MessageTemplate nowListening = MessageTemplate.MatchAll();
			
			@Override
			public void action() {
				// TODO Auto-generated method stub
				System.out.println("["+myAgent.getName()+"] STATE = " + state.toString());
				switch (state) {
				case BUILD_NEIGHBOURS_PREAMBLE:
					if (mainTeam == null || meetingLength == null){
						state = State.BUILD_DFS_PREAMBLE;
						break;
					}
			        DFAgentDescription template = new DFAgentDescription();
			        ServiceDescription sd = new ServiceDescription();
			        sd.setType("scheduling");
			        template.addServices(sd);
			        try {
			        	ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
			        	DFAgentDescription[] result = DFService.search(myAgent, template);
			        	others = new HashSet<AID>();
			            for (int i = 0; i < result.length; ++i) {
			              others.add(result[i].getName());
			              msg.addReceiver(result[i].getName());
			            }
			            msg.addReplyTo(myAgent.getAID());
			            msg.setContent(mainTeam);
			            msg.setConversationId(State.BUILD_NEIGHBOURS.toString());
			            msg.setReplyWith(mainTeam + "-neighbor-creation");
			            
			            nowListening = MessageTemplate.MatchInReplyTo(msg.getReplyWith());
			            state = State.BUILD_NEIGHBOURS;
			            myAgent.send(msg);
			          }
			          catch (FIPAException fe) {
			            fe.printStackTrace();
			          }
					break;
				case BUILD_NEIGHBOURS:
					ACLMessage reply = myAgent.receive(nowListening);
					if(reply != null) {
						if(others.contains(reply.getSender())){
							others.remove(reply.getSender());
							if(reply.getPerformative()==ACLMessage.CONFIRM){
								neighbors.add(reply.getSender());
							}
						}
						if(others.isEmpty()){
							StringBuffer neigh = new StringBuffer();
							for (AID n : neighbors) {
								if(neigh.length() != 0){
									neigh.append(",");
								}
								neigh.append(n.getName());
							}
							System.out.println("["+getName()+"] NEIGHBOURS = "+neigh.toString());
							state = State.BUILD_DFS_PREAMBLE;
						}
					} else {
						block();
					}
					break;
				case BUILD_DFS_PREAMBLE:
					break;
				}
			}
			
			@Override
			public boolean done() {
				// TODO Auto-generated method stub
				return false;
			}
		});
		
		//Responder for neighbor buildings
		addBehaviour(new CyclicBehaviour() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1923071880159548411L;

			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF), 
						MessageTemplate.MatchConversationId(State.BUILD_NEIGHBOURS.toString())));
				if (msg != null){
					System.out.println("["+getName()+"] NEIGHBOUR QUERY = "+msg.getContent());
					ACLMessage reply = msg.createReply();
					if(otherTeams.contains(msg.getContent())){
						reply.setPerformative(ACLMessage.CONFIRM);
						System.out.println("["+getName()+"] CONFIRMING "+msg.getContent());
					} else {
						reply.setPerformative(ACLMessage.DISCONFIRM);
						System.out.println("["+getName()+"] DISCONFIRMING "+msg.getContent());
					}
					myAgent.send(reply);
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

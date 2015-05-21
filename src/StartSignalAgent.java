import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JOptionPane;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;


public class StartSignalAgent extends Agent {


	private static final long serialVersionUID = -5845387012694936375L;
	Set<AID> agents = new HashSet<AID>();
	Map<AID, String> meetings = new HashMap<AID,String>();
	Double cost = null;
	
	@Override
	protected void setup() {
		super.setup();
		
		DFAgentDescription dfd = new DFAgentDescription();
	    dfd.setName(getAID());
	    ServiceDescription sd = new ServiceDescription();
	    sd.setType("scheduling-coord");
	    sd.setName("START-STOP");
	    dfd.addServices(sd);
	    try {
	      DFService.register(this, dfd);
	    }
	    catch (FIPAException fe) {
	      fe.printStackTrace();
	    }
	    
	    DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType("scheduling");
        template.addServices(sd1);
        try {
        	DFAgentDescription[] results = DFService.search(this, template);
        	for(DFAgentDescription result : results){
        		if(!result.getName().equals(this.getAID())){
        			agents.add(result.getName());
        		}
        	}
        	addBehaviour(new OneShotBehaviour() {
				
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public void action() {
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					for(AID agent:agents){
						msg.addReceiver(agent);
					}
					msg.setContent("START");
					myAgent.send(msg);
					myAgent.addBehaviour(new Behaviour() {
						
						/**
						 * 
						 */
						private static final long serialVersionUID = 1L;

						@Override
						public boolean done() {
							boolean end = true;
							for(AID agent:agents){
								if(!meetings.containsKey(agent)){
									end = false;
									break;
								}
							}
							if(end){
								end = (cost != null);
							}
							return end;
						}
						
						@Override
						public void action() {
							ACLMessage msg = myAgent.receive();
							if(msg!=null){
								if(msg.getConversationId().equals("COST")){
									StartSignalAgent.this.cost = Double.valueOf(msg.getContent());
								} else if(msg.getConversationId().equals("MEET")){
									meetings.put(msg.getSender(), msg.getContent());
								}
								if(done()){
									StringBuilder output = new StringBuilder();
									for(Entry<AID, String> entry:meetings.entrySet()){
										output.append("\n"+entry.getValue());
									}
									JOptionPane.showMessageDialog(null,
											"SCHEDULE:\n"+
											"(Cost: "+cost.toString()+")\n"+
											"--------------------------------\n"+
											output.toString());
								}
							} else {
								block();
							}
						}
					});
				}
			});
        } catch (FIPAException fe) {
        	fe.printStackTrace();
        }
	}
}

package main;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class BookBuyerAgent extends Agent {
    private String targetBookTitle;
    private AID[] sellerAgents;

    protected void setup() {
        System.out.println("Hello! Buyer-agent " + getAID().getName() +" is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetBookTitle = (String) args[0];
            System.out.println("Trying to buy " + targetBookTitle);

            addBehaviour(new TickerBehaviour(this, 10000) {
                protected void onTick() {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("book-selling");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following seller agents:");
                        sellerAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            sellerAgents[i] = result[i].getName();
                            System.out.println(sellerAgents[i].getName());
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    myAgent.addBehaviour(new RequestPerformer());
                }
            } );
        }
        else {
            System.out.println("No target book title specified");
            doDelete();
        }
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Printout a dismissal message
        System.out.println("Buyer-agent " + getAID().getName() + " terminating.");
    }

    private class RequestPerformer extends Behaviour {
        private AID bestSeller; 
        private int bestPrice; 
        private int repliesCnt = 0; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);
                    }
                    cfp.setContent(targetBookTitle);
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {

                            int price = Integer.parseInt(reply.getContent());
                            if (bestSeller == null || price < bestPrice) {

                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:

                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println(targetBookTitle + " successfully purchased from: " + reply.getSender().getName() + " by price: " + bestPrice);
                            myAgent.doDelete();
                        }
                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }
        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println(targetBookTitle + " is not available for sale.");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }
}
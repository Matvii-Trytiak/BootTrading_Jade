package main;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
public class BookSellerAgent extends Agent {

    private Hashtable catalogue;
    private BookSellerGui myGui;

    protected void setup() {
    	
    	System.out.println("Hello! Seller-agent " + getAID().getName() +" is ready.");
        catalogue = new Hashtable();
        myGui = new BookSellerGui(this);
        myGui.show();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        sd.setName("JADE-book-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new OfferRequestsServer());
        addBehaviour(new PurchaseOrdersServer());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        myGui.dispose();
        System.out.println("Seller-agent " + getAID().getName() + " terminating.");
    }
    
    public void updateCatalogue(final String title, final int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.put(title, new Integer(price));
                System.out.println(title + " added into catalogue by seller-agent: " + getAID().getName() + ", price = " + price);
            }
        } );
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mesTemp = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mesTemp);
            if (msg != null) {

                String title = msg.getContent();
                ACLMessage reply = msg.createReply();
                Integer price = (Integer) catalogue.get(title);
                if (price != null) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(price.intValue()));
                }
                else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mesTemp = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mesTemp);
            if (msg != null) {
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();
                Integer price = (Integer) catalogue.remove(title);
                if (price != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(title + " sold to agent " + msg.getSender().getName());
                }
                else {
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
}
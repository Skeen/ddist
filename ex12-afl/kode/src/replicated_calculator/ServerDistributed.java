package replicated_calculator;

import pointtopointqueue.*;
import multicastqueue.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.ArrayList;

public class ServerDistributed extends Thread implements Server 
{
    private HashMap<String,BigInteger> valuation = null;
    private HashMap<String,PointToPointQueueSenderEndNonRobust<ClientEvent>> clients = null;
    private PointToPointQueueReceiverEndNonRobust<ClientEvent> operationsFromClients = null;
    private MulticastQueueHistoryDecorator<ClientEvent> queue = null;

    //private final MulticastQueue.DeliveryGuarantee deliveryGuarantee = MulticastQueue.DeliveryGuarantee.TOTAL;
    private final MulticastQueue.DeliveryGuarantee deliveryGuarantee = MulticastQueue.DeliveryGuarantee.CAUSAL;
    //private final MulticastQueue.DeliveryGuarantee deliveryGuarantee = MulticastQueue.DeliveryGuarantee.FIFO;
    
    public ServerDistributed()
    {
        valuation = new HashMap<String,BigInteger>();
        clients = new HashMap<String,PointToPointQueueSenderEndNonRobust<ClientEvent>>();
        queue = new MulticastQueueHistoryDecorator(new MulticastQueueImpl<ClientEvent>(deliveryGuarantee));
    }

    private class calculator implements ClientEventVisitor
    {
        public void visit(ClientEventConnect eventConnect) { }
        public void visit(ClientEventDisconnect eventDisconnect) { }

        protected BigInteger valuate(String var) 
        {
            BigInteger val;
            synchronized(valuation) 
            {
                val = valuation.get(var);
            }
            if (val==null) 
            {
                val = BigInteger.ZERO;
            }
            return val;
        }

        public void visit(ClientEventAdd eventAdd) 
        {
            synchronized(valuation) 
            {
                valuation.put(eventAdd.res, valuate(eventAdd.left).add(valuate(eventAdd.right)));
            }
        }

        public void visit(ClientEventAssign eventAssign) 
        {
            synchronized(valuation) 
            {
                valuation.put(eventAssign.var, eventAssign.val);
            }
        }

        public void visit(ClientEventBeginAtomic _) 
        {
            throw new UnsupportedOperationException("ClientEventBeginAtomic");
        }

        public void visit(ClientEventCompare eventCompare) 
        {
            synchronized(valuation) 
            {
                valuation.put(eventCompare.res, BigInteger.valueOf(valuate(eventCompare.left).compareTo(valuate(eventCompare.right))));
            }
        }

        public void visit(ClientEventEndAtomic _) 
        {
            throw new UnsupportedOperationException("ClientEventEndAtomic");
        }

        public void visit(ClientEventMult eventMult) 
        {
            synchronized(valuation) 
            {
                valuation.put(eventMult.res, valuate(eventMult.left).multiply(valuate(eventMult.right)));
            }
        }

        public void visit(ClientEventRead eventRead) 
        {
            synchronized(valuation) 
            {
                eventRead.setVal(valuate(eventRead.var));
            }
        }
    }

    private class connectionManager implements ClientEventVisitor 
    {
        protected void acknowledgeEvent(ClientEvent event) 
        {
            synchronized(clients) 
            {
                PointToPointQueueSenderEndNonRobust<ClientEvent> toClient = clients.get(event.clientName);
                toClient.put(event);
            }
        }

        public void visit(ClientEventConnect eventConnect) 
        {
            final String clientName = eventConnect.clientName;
            final InetSocketAddress clientAddress = eventConnect.clientAddress;
            PointToPointQueueSenderEndNonRobust<ClientEvent> queueToClient = new PointToPointQueueSenderEndNonRobust<ClientEvent>(); 
            queueToClient.setReceiver(clientAddress);
            synchronized(clients) 
            {
                clients.put(clientName,queueToClient);
                acknowledgeEvent(eventConnect);
            }
        }

        public void visit(ClientEventDisconnect eventDisconnect) 
        {
            synchronized(clients) 
            {
                PointToPointQueueSenderEndNonRobust<ClientEvent> queueToClient = clients.remove(eventDisconnect.clientName);
                queueToClient.shutdown();
            }
        }

        // And just acknowledge the event, if its one of these
        public void visit(ClientEventAdd eventAdd) { acknowledgeEvent(eventAdd); }
        public void visit(ClientEventAssign eventAssign) { acknowledgeEvent(eventAssign); }
        public void visit(ClientEventBeginAtomic _) { acknowledgeEvent(_); }
        public void visit(ClientEventCompare eventCompare) { acknowledgeEvent(eventCompare); }
        public void visit(ClientEventEndAtomic _) { acknowledgeEvent(_); }
        public void visit(ClientEventMult eventMult) { acknowledgeEvent(eventMult); }
        public void visit(ClientEventRead eventRead) { acknowledgeEvent(eventRead); }
    }

    private class operationsToQueue extends Thread
    {
        /** 
         * Recieve a message from the client, and forward it to the multicastqueue
         */
        public void run() 
        {
            ClientEvent nextOperation;
            while ((nextOperation = operationsFromClients.get())!=null) 
            {
                queue.put(nextOperation);
            }
        }
    }

    private class queueToEvents extends Thread
    {
        /**
         * Recieve a message from the queue, and apply it to the hashmap
         */
        public void run()
        {
            MulticastMessage msg;
            while ((msg = queue.get()) != null) 
            {
                if(msg instanceof MulticastMessagePayload)
                {
                    handle((MulticastMessagePayload) msg);
                }
            }
        }

        public void handle(MulticastMessagePayload msg)
        {
            MulticastMessagePayload<ClientEvent> payloadMsg = (MulticastMessagePayload<ClientEvent>) msg;
            ClientEvent nextOperation = payloadMsg.getPayload();

            handle(msg, nextOperation);
            handle(nextOperation);
        }

        public void handle(MulticastMessagePayload msg, ClientEvent event)
        {
            try
            {
                InetSocketAddress myCon = new InetSocketAddress(InetAddress.getLocalHost(), Parameters.getServerPortForServers());
                // Check if it was us who sent this
                if(msg.getSender().equals(myCon))
                {
                    System.out.println("THATS MY CLIENT!");
                    event.accept(new connectionManager());
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        public void handle(ClientEvent event)
        {
            event.accept(new calculator());
            event.accept(new debugVisitor());
        }

    }

    /**
     * Open up connection to the clients
     */
    private int setupClientListener()
    {
        int hostPort = 0;
        operationsFromClients = null;
		try 
		{
			operationsFromClients = new PointToPointQueueReceiverEndNonRobust<ClientEvent>();
			hostPort = operationsFromClients.listenOnPort(Parameters.getServerPortForClients());
        }
        catch (IOException e) 
		{
			panicError(e);
		}
        return hostPort;
    }

    /**
     * Print the dangerous error
     */
    private void panicError(Exception e)
    {
        System.err.println("Cannot start server!");
        System.err.println("Check your network connection!");
        System.err.println("Check that no other service runs on port " + Parameters.getServerPortForClients() + "!");
        System.err.println();
        System.err.println(e);
        System.exit(-1);
    }

    /**
     * Start the two threads!
     */
    private void startThreads()
    {
        operationsToQueue otq = new operationsToQueue();
        otq.start();

        queueToEvents qte = new queueToEvents();
        qte.start();
    }

    /**
     * Create a multicast queue
     */
    public void createGroup(int port) 
	{
		int serversPort = -1;
        int clientsPort = -1;
		try 
		{
            serversPort = queue.createGroup(Parameters.getServerPortForServers(), deliveryGuarantee);
		} 
		catch (IOException e) 
		{
			panicError(e);
		}
        clientsPort = setupClientListener();
        Parameters.setPorts(clientsPort, -1, serversPort);
        startThreads();
    }
    
    /**
     * Join the multicast queue currently active
     */
    public void joinGroup(InetSocketAddress knownPeer) 
	{
        int serversPort = -1;
        int clientsPort = -1;
        try 
		{
            serversPort = queue.joinGroup(Parameters.getServerPortForServers(), knownPeer, deliveryGuarantee);
		} 
		catch (IOException e) 
		{
			panicError(e);
		}
        clientsPort = setupClientListener();
        Parameters.setPorts(clientsPort, -1, serversPort);

        ArrayList<MulticastMessage> history = queue.getHistory();
        for(MulticastMessage msg : history)
        {
            if(msg instanceof MulticastMessagePayload)
            {
                MulticastMessagePayload<ClientEvent> payloadMsg = (MulticastMessagePayload<ClientEvent>) msg;
                ClientEvent nextOperation = payloadMsg.getPayload();

                new queueToEvents().handle(nextOperation);
            }
        }

        startThreads();
    }
    
    /**
     * Leave the group, then shutdown the connection
     */
    public void leaveGroup() 
	{
        queue.leaveGroup();

		operationsFromClients.shutdown();
		for (String client : clients.keySet()) 
		{
			clients.remove(client).shutdown();
		}		
    }
}

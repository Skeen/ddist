package replicated_calculator;
import pointtopointqueue.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.net.InetSocketAddress;

/**
 * 
 * An stand-along implementation of a server. It simply keeps the variables in 
 * a hash map and executes the commands in the natural way on this hash map.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 *
 */

public class ServerStandalone extends Thread implements ClientEventVisitor, Server 
{
    protected final HashMap<String,BigInteger> valuation 
	= new HashMap<String,BigInteger>();
    protected final HashMap<String,PointToPointQueueSenderEndNonRobust<ClientEvent>> clients 
	= new HashMap<String,PointToPointQueueSenderEndNonRobust<ClientEvent>>();
    private PointToPointQueueReceiverEndNonRobust<ClientEvent> operationsFromClients;
    
    public void createGroup(int port) 
	{
		operationsFromClients = null;
        int clientsPort = -1;
        int serversPort = -1;
		try 
		{
			operationsFromClients = new PointToPointQueueReceiverEndNonRobust<ClientEvent>();
			serversPort = operationsFromClients.listenOnPort(Parameters.getServerPortForClients());
		} 
		catch (IOException e) 
		{
			System.err.println("Cannot start server!");
			System.err.println("Check your network connection!");
			System.err.println("Check that no other service runs on port " + Parameters.getServerPortForClients() + "!");
			System.err.println();
			System.err.println(e);
			System.exit(-1);
		}
        Parameters.setPorts(clientsPort, -1, serversPort);
		this.start();
    }
    
    public void joinGroup(InetSocketAddress knownPeer) 
	{
		throw new UnsupportedOperationException("joinGroup");
    }
    
    /**
     * No group to leave, so simply shutsdown.
     */
    public void leaveGroup() 
	{
		operationsFromClients.shutdown();
		for (String client : clients.keySet()) 
		{
			clients.remove(client).shutdown();
		}		
    }
    
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
    
    protected void acknowledgeEvent(ClientEvent event) 
	{
		synchronized(clients) 
		{
			PointToPointQueueSenderEndNonRobust<ClientEvent> toClient = clients.get(event.clientName);
			toClient.put(event);
		}
    }
    
    public void visit(ClientEventAdd eventAdd) 
	{
		synchronized(valuation) 
		{
			valuation.put(eventAdd.res, valuate(eventAdd.left).add(valuate(eventAdd.right)));
			acknowledgeEvent(eventAdd);
		}
    }
    
    public void visit(ClientEventAssign eventAssign) 
	{
		synchronized(valuation) 
		{
			valuation.put(eventAssign.var, eventAssign.val);
			acknowledgeEvent(eventAssign);
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
			acknowledgeEvent(eventCompare);
		}
    }
    
    /**
     * Connects a client given a connection event from the client. 
     * This is done
     * 
     * @param clientName
     * @param clientAddress
     */
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
    
    /**
     * Disconnects a client on a disconnect event from the client. 
     * 
     * @param eventDisconnect
     */
    public void visit(ClientEventDisconnect eventDisconnect) 
	{
		synchronized(clients) 
		{
			PointToPointQueueSenderEndNonRobust<ClientEvent> queueToClient = clients.remove(eventDisconnect.clientName);
			queueToClient.shutdown();
		}
    }
    
    /**
     * Not supported yet.
     * @param _
     */
    public void visit(ClientEventEndAtomic _) 
	{
		throw new UnsupportedOperationException("ClientEventEndAtomic");
    }
    
    /**
     * Multiplies to variables on a multiplication event from the client.
     * 
     * @param eventMult The multiplication event from the client.
     */
    public void visit(ClientEventMult eventMult) 
	{
		synchronized(valuation) 
		{
			valuation.put(eventMult.res, valuate(eventMult.left).multiply(valuate(eventMult.right)));
			acknowledgeEvent(eventMult);
		}
    }
    
    /**
     * Reads a variable on a read event from the client.
     * 
     * @param eventRead The read event from the client.
     */
    public void visit(ClientEventRead eventRead) 
	{
		synchronized(valuation) 
		{
			eventRead.setVal(valuate(eventRead.var));
			acknowledgeEvent(eventRead);
		}
    }
    
    /** 
     * Keeps getting the next operation from a server and then visits the operation.
     * Sub-classes have to implement the visiting methods.
     */
    public void run() 
	{
		ClientEvent nextOperation;
		while ((nextOperation = operationsFromClients.get())!=null) 
		{
			nextOperation.accept(this);
		}
    }
    
}

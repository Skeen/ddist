package multicastqueue;

import java.io.Serializable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetSocketAddress;
import java.net.InetAddress;

/**
 *
 * An implementation of MulticastQueue, which obly implements the FIFO
 * delivery guarantee.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastQueueFifoOnly<E extends Serializable> 
    extends Thread implements MulticastQueue<E>  {

    /**
     * The address on which we listen for incoming messages.
     */      
    private InetSocketAddress myAddress;

    /**
     * Used to signal that the queue is leaving the peer group. 
     */
    private boolean isLeaving = false;

    /**
     * Used to signal that no more elements will be added to the queue
     * of pending gets.
     */
    private boolean noMoreGetsWillBeAdded = false;

    /**
     * The thread which handles outgoing traffic.
     */
    private SendingThread sendingThread;

    /**
     * The peers who have a connection to us. Used to make sure that
     * we do not close down the receiving end of the queue before all
     * sending to the queue is done. Not strictly needed, but nicer.
     */
    private HashSet<InetSocketAddress> hasConnectionToUs;

    /**
     * This class of objects is used to send a join request.  It will
     * be sent by a new peer to one of the existing peers in the peer
     * group, called the "joining point" below.  Made as a static
     * class to avoid the serialization considers the outer class.
     */
    static private class JoinRequestMessage extends MulticastMessage {
	public JoinRequestMessage(InetSocketAddress myAddress) {
	    super(myAddress);
	}
	public String toString() {
	    return "(JoinRequestMessage from " + getSender()  + ")";	
	}
    }

    /**
     * This class of objects is used to tell the rest of the group
     * that a new peer joined at our site. Made as a static class to
     * avoid the serialization considers the outer class.
     */
    static class JoinRelayMessage extends MulticastMessage {
	private InetSocketAddress addressOfJoiner;
	public JoinRelayMessage(InetSocketAddress sender, 
				InetSocketAddress joiner) {
	    super(sender);
	    addressOfJoiner = joiner;
	}
	/** 
	 * @return The address of the peer who originally sent the
	 *         join request message that resulted in this join
	 *         message.
	 */
	public InetSocketAddress getAddressOfJoiner() {
	    return addressOfJoiner;
	}
	public String toString() {
	    return "(JoinRelayMessage for " + getAddressOfJoiner() + 
		" from " + getSender() + ")";
	}
    }

    /**
     * This class of objects is used to send the address of the
     * existing peers to a newly joined member. Happens in response to
     * a join relay message. Made as a static class to avoid the
     * serialization considers the outer class.
     */
    static private class WellcomeMessage extends MulticastMessage {
	public WellcomeMessage(InetSocketAddress myAddress) {
	    super(myAddress);
	}
	public String toString() {
	    return "(WellcomeMessage from " + getSender()  + ")";	
	}
    }

    /**
     * This class of objects is used to send the address of the
     * existing peers to a leaving member. Happens in response to
     * a leave message. Made as a static class to avoid the
     * serialization considers the outer class.
     */
    static private class GoodbuyMessage extends MulticastMessage {
	public GoodbuyMessage(InetSocketAddress myAddress) {
	    super(myAddress);
	}
	public String toString() {
	    return "(GoodbuyMessage from " + getSender()  + ")";	
	}
    }

    /**
     * The incoming message queue. All other peers send their messages
     * to this queue.
     */
    private PointToPointQueueReceiverEnd<MulticastMessage> incoming;

    /**
     * Keeping track of the outgoing message queues, stored under the
     * corresponding internet address.
     */
    private ConcurrentHashMap<InetSocketAddress,PointToPointQueueSenderEnd<MulticastMessage>> outgoing;

    /**
     * Objects pending delivering locally.
     */
    private ConcurrentLinkedQueue<MulticastMessage> pendingGets;
    
    /**
     * Objects pending sending.
     */
    private ConcurrentLinkedQueue<E> pendingSends;

    public MulticastQueueFifoOnly() {
	incoming = new PointToPointQueueReceiverEndNonRobust<MulticastMessage>();
	pendingGets = new ConcurrentLinkedQueue<MulticastMessage>();
	pendingSends = new ConcurrentLinkedQueue<E>();
	outgoing = new ConcurrentHashMap<InetSocketAddress,PointToPointQueueSenderEnd<MulticastMessage>>();
	sendingThread = new SendingThread();
	hasConnectionToUs = new HashSet<InetSocketAddress>();
	sendingThread.start();
    }
	
	public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) 
        throws IOException 
    {
        assert (deliveryGuarantee==DeliveryGuarantee.NONE || 
                deliveryGuarantee==DeliveryGuarantee.FIFO) 
            : "Can at best implement FIFO";
        // Try to listen on the given port. Exception are propagated out.
        port = incoming.listenOnPort(port);

        // Record our address
        InetAddress localhost = InetAddress.getLocalHost();
        String localHostAddress = localhost.getCanonicalHostName();
        myAddress = new InetSocketAddress(localHostAddress, port);

        // Buffer a message that we have joined the group.
        addAndNotify(pendingGets, new MulticastMessageJoin(myAddress));

        // Start the receiveing thread.
        this.start();
        return port;
    }

    public int joinGroup(int port, InetSocketAddress knownPeer, 
            DeliveryGuarantee deliveryGuarantee) 
        throws IOException 
    {
        assert (deliveryGuarantee==DeliveryGuarantee.NONE || 
                deliveryGuarantee==DeliveryGuarantee.FIFO) 
            : "Can at best implement FIFO";

        // Try to listen on the given port. Exceptions are propagated
        // out of the method.
        port = incoming.listenOnPort(port);

        // Record our address.
        InetAddress localhost = InetAddress.getLocalHost();
        String localHostAddress = localhost.getCanonicalHostName();
        myAddress = new InetSocketAddress(localHostAddress, port);

        // Make an outgoing connection to the known peer.
        PointToPointQueueSenderEnd<MulticastMessage> out 
            = connectToPeerAt(knownPeer);	
        // Send the known peer our address. 
        JoinRequestMessage joinRequestMessage 
            = new JoinRequestMessage(myAddress);
        out.put(joinRequestMessage);
        // When the known peer receives the join request it will
        // connect to us, so let us remember that she has a connection
        // to us.
        hasConnectionToUs.add(knownPeer);	

        // Buffer a message that we have joined the group.
        addAndNotify(pendingGets, new MulticastMessageJoin(myAddress));

        // Start the receiving thread
        this.start();
        return port;
    }

    public MulticastMessage get() {		
	// Now an object is ready in pendingObjects, unless we are
	// shutting down. 
	synchronized (pendingGets) {
	    waitForPendingGetsOrReceivedAll();
	    if (pendingGets.isEmpty()) {
		return null;
		// By contract we signal shutdown by returning null.
	    } else {
		return pendingGets.poll();
	    }
	}
    }

    /**
     * This is the receiving thread. This is just a dispatcher: it
     * will receive the incoming message and call the respective
     * handlers.
     */
    @SuppressWarnings("unchecked")
    public void run() {
	log("starting receiving thread.");
	MulticastMessage msg = incoming.get();
	/* By contract we know that msg == null only occurs if
         * incoming is shut down, which we are the only ones that can
         * do, so we use that as a way to kill the receiving thread
         * when that is needed. We shut down the incoming queue when
         * it happens that we are leaving down and all peers notified
         * us that they shut down their connection to us, at which
         * point no more message will be added to the incoming
         * queue. */
	while (msg != null) {
	    if (msg instanceof MulticastMessagePayload) {
		MulticastMessagePayload pmsg = (MulticastMessagePayload)msg;
		handle(pmsg);
	    } else if (msg instanceof MulticastQueueFifoOnly.JoinRequestMessage) {
		JoinRequestMessage jrmsg = (JoinRequestMessage)msg;
		handle(jrmsg);
	    } else if (msg instanceof JoinRelayMessage) {
		JoinRelayMessage jmsg = (JoinRelayMessage)msg;
		handle(jmsg);
	    } else if (msg instanceof MulticastQueueFifoOnly.WellcomeMessage) {
		WellcomeMessage wmsg = (WellcomeMessage)msg;
		handle(wmsg);
	    } else if (msg instanceof MulticastMessageLeave) {
		MulticastMessageLeave lmsg = (MulticastMessageLeave)msg;
		handle(lmsg);
	    } else if (msg instanceof GoodbuyMessage) {
		GoodbuyMessage gmsg = (GoodbuyMessage)msg;
		handle(gmsg);
	    }	    
	    msg = incoming.get();
	}
	/* Before we terminate we notify callers who are blocked in
	 * out get() method that no more gets will be added to the
	 * buffer pendingGets. This allows them to return with a null
	 * in case no message are in that buffer. */	
	noMoreGetsWillBeAdded = true;
	synchronized (pendingGets) {
	    pendingGets.notifyAll();
	}
	log("stopping receiving thread.");
    }

    /**
     * Will send a copy of the message to all peers who at some point
     * sent us a wellcome message and who did not later send us a
     * goodbuy message, unless we are leaving the peer group.
     */
    private void sendToAll(MulticastMessage msg) {
	if (isLeaving!=true) {
	    /* Send to self. */
	    incoming.put(msg);
	    /* Then send to the others. */
	    sendToAllExceptMe(msg);
	}
    }
    private void sendToAllExceptMe(MulticastMessage msg) {
	if (isLeaving!=true) {
	    for (PointToPointQueueSenderEnd<MulticastMessage> out : 
		     outgoing.values()) {
		out.put(msg);
	    }
	}
    }

    /**
     * A join request message is handled by connecting to the peer who
     * wants to join and then broadcasting a join message to all peers
     * in the current group, so that they cannot connect to the new
     * peer too.
     */
    private void handle(JoinRequestMessage jrmsg) {
	log(jrmsg);	
	// When the joining peer sent the join request it connected to
	// us, so let us remember that she has a connection to us. 
	synchronized(hasConnectionToUs) {
	    hasConnectionToUs.add(jrmsg.getSender()); 
	}
	// Buffer a join message so it can be gotten. 
	addAndNotify(pendingGets, new MulticastMessageJoin(jrmsg.getSender()));

	// Then we tell the rest of the group that we have a new member.
	sendToAllExceptMe(new JoinRelayMessage(myAddress, jrmsg.getSender()));
	// Then we connect to the new peer. 
	connectToPeerAt(jrmsg.getSender());
    }

    /**
     * A join message is handled by making a connection to the new
     * peer plus sending her a wellcome message with our own address.
     */
    private void handle(JoinRelayMessage jmsg) {
	log(jmsg);
	assert (!(jmsg.getSender().equals(myAddress))) 
	    : "Got a join message sent by myself!";
	assert (!(jmsg.getAddressOfJoiner().equals(myAddress))) 
	    : "Got a join message about my own joining!";

	// Buffer a join message so it can be gotten.
	addAndNotify(pendingGets, new MulticastMessageJoin(jmsg.getAddressOfJoiner()));

	// Connect to the new peer and bid him welcome. 
	PointToPointQueueSenderEnd<MulticastMessage> out 
	    = connectToPeerAt(jmsg.getAddressOfJoiner());
	out.put(new WellcomeMessage(myAddress));
	// When this peer receives the wellcome message it will
	// connect to us, so let us remember that she has a connection
	// to us.
	 
	synchronized(hasConnectionToUs) {
	    hasConnectionToUs.add(jmsg.getAddressOfJoiner());
	}
    }

    /**
     * A wellcome message is handled by making a connection to the
     * existing peer who sent the wellcome message. After this,
     * SendToAll will send a copy also to the peer who sent us this
     * wellcome message.
     */
    private void handle(WellcomeMessage wmsg) {
	log(wmsg);
	// When the sender sent us the wellcome message it connect to
	// us, so let us remember that she has a connection to us.
	synchronized(hasConnectionToUs) {
	    hasConnectionToUs.add(wmsg.getSender());
	}
	connectToPeerAt(wmsg.getSender());
    }
    
    /**
     * A payload message is handled by adding it to the queue of
     * received messages, so that it can be gotten.
     */
    private void handle(MulticastMessagePayload<E> pmsg) {
	log(pmsg);       
	addAndNotify(pendingGets, pmsg);
    }

    /**
     * A leave message is handled by removing the connection to the
     * leaving peer.
     */
    private void handle(MulticastMessageLeave lmsg) {
	log(lmsg);
	addAndNotify(pendingGets, lmsg);
	InetSocketAddress address = lmsg.getSender();
	if (!address.equals(myAddress)) {
	    disconnectFrom(address);
	} else {
	    // That was my own leave message. If I'm the only one left
	    // in the group, then this means that I can safely shut
	    // down.
	    if (hasConnectionToUs.isEmpty()) {
		incoming.shutdown();
	    }
	} 
    }

    /**
     * A goodbuy message is produced as response to a leave message
     * and is handled by closing the connection to the existing peer
     * who sent the goodbuy message. After this, SendToAll will not
     * send a copy to the peer who sent us this goodbuy message.
     */
    private void handle(GoodbuyMessage gmsg) {
    	log(gmsg);
	// When the peer sent us the goodbuy message, it closed its
	// connection to us, so let us remember that.
	synchronized(hasConnectionToUs) {
	    hasConnectionToUs.remove(gmsg.getSender());
	    log("now " + hasConnectionToUs.size() + " has connections to us!");
	    // If we are leaving and that was the last goodbuy
	    // message, then we can shut down the incoming queue and
	    // terminate the receving thread.
	    if (hasConnectionToUs.isEmpty() && isLeaving) {
		// If the receiving thread is blocked on the incoming
		// queue, it will be woken up and receive a null when
		// the queue is empty, which will tell it that we have
		// received all messages.
		incoming.shutdown();
	    }
	}
    }

    /**
     * Used by callers to wait for objects to enter pendingGets. When
     * the method returns, then either the collection is non-empty, or
     * the multicast queue has seen its own leave message arrive on
     * the incoming stream.
     */
    private void waitForPendingGetsOrReceivedAll() {
	synchronized (pendingGets) {
	    while (pendingGets.isEmpty() && !noMoreGetsWillBeAdded) {
		try {
		    // We will be woken up if an object arrives or the
		    // we received all.		     
		    pendingGets.wait();
		} catch (InterruptedException e) {
		    // Probably shutting down. The while condition
		    // will ensure proper behavior in case of some
		    // other interruption.
		}
	    }
	    // Now: pendingGets is non empty or we received all there
	    // is to receive.
	}	
    }

    /**
     * Used to add an element to a collection and wake up one thread
     * waiting for elements on the collection.
     */
    private <T> void addAndNotify(Collection<T> coll, T object) {
	synchronized (coll) {
	    coll.add(object);
	    // Notify that there is a new message. 
	    coll.notify();
	}
    }

    /**
     * Used to create an outgoing queue towards the given address,
     * including the addition of that queue to the set of queues.
     *
     * @param address The address of the peer we want to connect
     *        to. Returns null when attempting to make connection to
     *        self.
     */
    private PointToPointQueueSenderEnd<MulticastMessage> 
	connectToPeerAt(InetSocketAddress address) {
	assert (!address.equals(myAddress)) : "Cannot connect to self.";
	// Do we have a connection already?
	PointToPointQueueSenderEnd<MulticastMessage> out 
	    = outgoing.get(address);
	assert (out == null) : "Cannot connect twice to same peer!";
	out = new PointToPointQueueSenderEndNonRobust<MulticastMessage>();
	out.setReceiver(address);
	outgoing.put(address, out);
	log(myAddress + ": connects to " + address);
	return out;
    }



    /***
     ** The part which receives puts, buffers then and sends them.
     **/
    public void put(E object) {
	synchronized(pendingSends) {
	    assert (isLeaving==false) 
		: "Cannot put objects after calling leaveGroup()";
	    addAndNotify(pendingSends, object);
	}
    }

    public void leaveGroup() {
	synchronized (pendingSends) {
	    assert (isLeaving != true): "Already left the group!"; 
	    sendToAll(new MulticastMessageLeave(myAddress));
	    isLeaving = true;
	    // We wake up the sending thread. If pendingSends happen
	    // to be empty now, the sending thread will know that we
	    // are shutting down, so it will not starting waiting on
	    // pendingSends again.
	    pendingSends.notify();
	}
    }

    /**
     * Used by the sending thread to wait for objects to enter the
     * collection or us having left the group. When the method
     * returns, then either the collection is non-empty, or the
     * multicast queue was called in leaveGroup();
     */
    private void waitForPendingSendsOrLeaving() {
	synchronized (pendingSends) {
	    while (pendingSends.isEmpty() && !isLeaving) {
		try {
		    // We will be woken up if an object arrives or we
		    // are leaving the group. Both might be the case
		    // at the same time.
		    pendingSends.wait();
		} catch (InterruptedException e) {
		    // Probably leaving. The while condition will
		    // ensure proper behavior in case of some other
		    // interruption.
		}
	    }
	    // Now: pendingSends is non empty or we are leaving the group.
	}	
    }

    /**
     * Will take objects from pendingSends and send them to all peers.
     * If the queue empties and leaveGroup() was called, then the
     * queue will remain empty, so we can terminate.
     */
    private class SendingThread extends Thread {
	public void run() {	    
	    log("starting sending thread.");
	    // As long as we are not leaving or there are objects to
	    // send, we will send them.
	    waitForPendingSendsOrLeaving();
	    E object = pendingSends.poll();
	    while (object != null) {
		sendToAll(new MulticastMessagePayload<E>(myAddress, object));
		waitForPendingSendsOrLeaving();
		object = pendingSends.poll();
	    }
	    log("shutting down outgoing connections.");
	    synchronized (outgoing) {
		for (InetSocketAddress address : outgoing.keySet()) {
		    disconnectFrom(address);
		}
	    }
	    log("stopping sending thread.");
	}
    }

    private void disconnectFrom(InetSocketAddress address) {
	synchronized (outgoing) {
	    PointToPointQueueSenderEnd<MulticastMessage> out 
		= outgoing.get(address);
	    if (out != null) {
		outgoing.remove(address);
		out.put(new GoodbuyMessage(myAddress));
		log("disconnected from " + address);
		out.shutdown();
	    }
	}
    }

    /***
     ** HELPERS FOR DEBUGGING
     **/
    protected boolean log = false;

    public void printLog() {
	log = true;
    }

    protected void log(String msg) {
	if (log) System.out.println(myAddress + " said: " + msg);
    }

    protected void log(MulticastMessage msg) {
	if (log) System.out.println(myAddress + " received: " + msg);
    }
 
}


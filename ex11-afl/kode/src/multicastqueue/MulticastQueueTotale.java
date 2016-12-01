package multicastqueue;

/*
 * Changelog:
 * 	added MulticastMessageContaioner
 *  added AcknowlegdeMessage class
 *  changed pendingGets to a PriorityBlockingQueue
 *  changed all PointToPoint Senders and Receivers to send MulticastMessageContainers
 *  added sendToKnown
 *  changed all out.put to use the new method instead
 *  added timestamp to all send methods
 *  Log now includes timestamp and all messages send by this peer
 *  Now sending acknowlegdement messages
 *  Implemented Timestamp according to the book
 *  fixed bug, connecting to peer when receiving a leave message
 *  add notAcknowledgeMessages and use ack msg to approve PayloadMessages
 *  now approving LeaveMessages with the GoodbyeMessage
 *  only get if first message in queue is approved
 *  fixed: not calling notify on pendingGets when approving a message
 *  fixed: on joinRequest adding a JoinMessage to pendingGets with ts 0 instead of the current ts
 *  fixed: never checking if leaveMessage was approved.
 *
 * Todo:
 *  testing
 */

import pointtopointqueue.PointToPointQueueReceiverEnd;
import pointtopointqueue.PointToPointQueueReceiverEndNonRobust;
import pointtopointqueue.PointToPointQueueSenderEnd;
import pointtopointqueue.PointToPointQueueSenderEndNonRobust;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 
 * An implementation of MulticastQueue, which implements the Total delivery
 * guarantee.
 */
public class MulticastQueueTotale<E extends Serializable> extends Thread
		implements MulticastQueue<E> {

	/**
	 * The address on which we listen for incoming messages.
	 */
	private InetSocketAddress myAddress;

	/**
	 * Used to signal that the queue is leaving the peer group.
	 */
	private boolean isLeaving = false;
	
	/**
	 * Used to signal that no more elements will be added to the queue of
	 * pending gets.
	 */
	private boolean noMoreGetsWillBeAdded = false;

	/**
	 * The thread which handles outgoing traffic.
	 */
	private SendingThread sendingThread;

	/**
	 * The peers who have a connection to us. Used to make sure that we do not
	 * close down the receiving end of the queue before all sending to the queue
	 * is done. Not strictly needed, but nicer.
	 */
	private HashSet<InetSocketAddress> hasConnectionToUs;

	/**
	 * Timestamp object
	 */
	private Timestamp timestamp;

	/**
	 * 
	 * A MultiMessage Container witch adds a timestamp to the message.
	 */
	@SuppressWarnings("serial")
	static private class MulticastMessageContainer implements Serializable,
			Comparable<MulticastMessageContainer> {
		MulticastMessage message = null;
		int timestamp;

		MulticastMessageContainer(MulticastMessage msg, int timestamp) {
			this.message = msg;
			this.timestamp = timestamp;
		}

		public int getTimestamp() {
			return timestamp;
		}

		public MulticastMessage getMessage() {
			return message;
		}

		public String toString() {
			return "" + this.message + " ts:" + this.timestamp;
		}

		@Override
		public int compareTo(MulticastMessageContainer o) {
			if (this.timestamp == o.getTimestamp())
				return this.getMessage().getSender().toString()
						.compareTo(o.getMessage().getSender().toString());
			else
				return this.getTimestamp() - o.getTimestamp();
		}
	}

	/**
	 * 
	 * This represent the acknowledgment.
	 */
	@SuppressWarnings("serial")
	static private class AcknowledgementMessage extends MulticastMessage {
		private int originalTimestamp;

		AcknowledgementMessage(InetSocketAddress sender,int originalTimestamp) {
			super(sender);
			this.originalTimestamp = originalTimestamp;
		}

		public String toString() {
			return "(Acknowledgement from " + getSender() + " with orgts: "+ originalTimestamp +")";
		}
		
		public int getOriginalTimestamp(){
			return this.originalTimestamp;
		}
	}

	/**
	 * This class of objects is used to send a join request. It will be sent by
	 * a new peer to one of the existing peers in the peer group, called the
	 * "joining point" below. Made as a static class to avoid the serialization
	 * considers the outer class.
	 */
	@SuppressWarnings("serial")
	static private class JoinRequestMessage extends MulticastMessage {
		public JoinRequestMessage(InetSocketAddress myAddress) {
			super(myAddress);
		}

		public String toString() {
			return "(JoinRequestMessage from " + getSender() + ")";
		}
	}

	/**
	 * This class of objects is used to tell the rest of the group that a new
	 * peer joined at our site. Made as a static class to avoid the
	 * serialization considers the outer class.
	 */
	@SuppressWarnings("serial")
	static private class JoinRelayMessage extends MulticastMessage {
		private InetSocketAddress addressOfJoiner;

		public JoinRelayMessage(InetSocketAddress sender,
				InetSocketAddress joiner) {
			super(sender);
			addressOfJoiner = joiner;
		}

		/**
		 * @return The address of the peer who originally sent the join request
		 *         message that resulted in this join message.
		 */
		public InetSocketAddress getAddressOfJoiner() {
			return addressOfJoiner;
		}

		public String toString() {
			return "(JoinRelayMessage for " + getAddressOfJoiner() + " from "
					+ getSender() + ")";
		}
	}

	/**
	 * This class of objects is used to send the address of the existing peers
	 * to a newly joined member. Happens in response to a join relay message.
	 * Made as a static class to avoid the serialization considers the outer
	 * class.
	 */
	@SuppressWarnings("serial")
	static private class WelcomeMessage extends MulticastMessage {
		public WelcomeMessage(InetSocketAddress myAddress) {
			super(myAddress);
		}

		public String toString() {
			return "(WellcomeMessage from " + getSender() + ")";
		}
	}

	/**
	 * This class of objects is used to send the address of the existing peers
	 * to a leaving member. Happens in response to a leave message. Made as a
	 * static class to avoid the serialization considers the outer class.
	 */
	@SuppressWarnings("serial")
	static private class GoodbyeMessage extends MulticastMessage {
		public GoodbyeMessage(InetSocketAddress myAddress) {
			super(myAddress);
		}

		public String toString() {
			return "(GoodbuyMessage from " + getSender() + ")";
		}
	}

	/**
	 * The incoming message queue. All other peers send their messages to this
	 * queue.
	 */
	private PointToPointQueueReceiverEnd<MulticastMessageContainer> incoming;

	/**
	 * Keeping track of the outgoing message queues, stored under the
	 * corresponding internet address.
	 */
	private ConcurrentHashMap<InetSocketAddress, PointToPointQueueSenderEnd<MulticastMessageContainer>> outgoing;

	/**
	 * Objects pending delivering locally.
	 */
	private PriorityBlockingQueue<MulticastMessageContainer> pendingGets;

	/**
	 * Objects pending sending.
	 */
	private ConcurrentLinkedQueue<E> pendingSends;

	/**
	 * Message that have not been acknowledged
	 */
	private ConcurrentHashMap<Integer,List<InetSocketAddress>> notAcknowledgedMessages;

	/**
	 * Used in the notAcknowledgeMessages Map as key for our leave message 
	 */
	private final int LEAVE_MSG_FAKE_TIMESTAMP = -1;
	
	public MulticastQueueTotale() {
		incoming = new PointToPointQueueReceiverEndNonRobust<MulticastMessageContainer>();
		pendingGets = new PriorityBlockingQueue<MulticastMessageContainer>();
		pendingSends = new ConcurrentLinkedQueue<E>();
		outgoing = new ConcurrentHashMap<InetSocketAddress, PointToPointQueueSenderEnd<MulticastMessageContainer>>();
		sendingThread = new SendingThread();
		hasConnectionToUs = new HashSet<InetSocketAddress>();
		notAcknowledgedMessages = new ConcurrentHashMap<Integer, List<InetSocketAddress>>();
		timestamp = new Timestamp();
		sendingThread.start();
	}

	public int createGroup(int port, DeliveryGuarantee deliveryGuarantee)
			throws IOException {
		assert (deliveryGuarantee == DeliveryGuarantee.NONE || deliveryGuarantee == DeliveryGuarantee.FIFO) : "Can at best implement FIFO";
		// Try to listen on the given port. Exception are propagated out.
		port = incoming.listenOnPort(port);

		// Record our address
		InetAddress localhost = InetAddress.getLocalHost();
		String localHostAddress = localhost.getCanonicalHostName();
		myAddress = new InetSocketAddress(localHostAddress, port);

		// Buffer a message that we have joined the group.
		synchronized (timestamp) {
			addAndNotify(pendingGets, new MulticastMessageJoin(myAddress),
					timestamp.getTimestamp());
		}

		// Start the receiving thread.
		this.start();
        return port;
	}

	public int joinGroup(int port, InetSocketAddress knownPeer,
			DeliveryGuarantee deliveryGuarantee) throws IOException {
		assert (deliveryGuarantee == DeliveryGuarantee.NONE || deliveryGuarantee == DeliveryGuarantee.FIFO) : "Can at best implement FIFO";

		// Try to listen on the given port. Exceptions are propagated
		// out of the method.
		port = incoming.listenOnPort(port);

		// Record our address.
		InetAddress localhost = InetAddress.getLocalHost();
		String localHostAddress = localhost.getCanonicalHostName();
		myAddress = new InetSocketAddress(localHostAddress, port);

		// Send the known peer our address.
		JoinRequestMessage joinRequestMessage = new JoinRequestMessage(
				myAddress);
		sendToknownPeer(joinRequestMessage, knownPeer);
		// When the known peer receives the join request it will
		// connect to us, so let us remember that she has a connection
		// to us.
		hasConnectionToUs.add(knownPeer);

		// Buffer a message that we have joined the group.
		synchronized (timestamp) {
			addAndNotify(pendingGets, new MulticastMessageJoin(myAddress),
					timestamp.getTimestamp());
		}

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
				MulticastMessageContainer result = pendingGets.poll();
				return result.getMessage();
			}
		}
	}

	/**
	 * This is the receiving thread. This is just a dispatcher: it will receive
	 * the incoming message and call the respective handlers.
	 */
	@SuppressWarnings("unchecked")
	public void run() {
		log("starting receiving thread.");
		MulticastMessageContainer mmc = incoming.get();
		/*
		 * By contract we know that msg == null only occurs if incoming is shut
		 * down, which we are the only ones that can do, so we use that as a way
		 * to kill the receiving thread when that is needed. We shut down the
		 * incoming queue when it happens that we are leaving down and all peers
		 * notified us that they shut down their connection to us, at which
		 * point no more message will be added to the incoming queue.
		 */
		while (mmc != null) {
			MulticastMessage msg = mmc.getMessage();
			int ts = mmc.getTimestamp();
			log(mmc);
			synchronized (timestamp) {
				timestamp.maxOfTimestamps(mmc);
			}

			if (msg instanceof AcknowledgementMessage) {
				AcknowledgementMessage amsg = (AcknowledgementMessage) msg;
				handle(amsg, ts);
			} else {

				if (msg instanceof MulticastMessagePayload) {
					sendAcknowlegdement(msg.getSender(),ts);
					MulticastMessagePayload<E> pmsg = (MulticastMessagePayload<E>) msg;
					handle(pmsg, ts);
				} else if (msg instanceof JoinRequestMessage) {
					JoinRequestMessage jrmsg = (JoinRequestMessage) msg;
					handle(jrmsg, ts);
				} else if (msg instanceof JoinRelayMessage) {
					JoinRelayMessage jmsg = (JoinRelayMessage) msg;
					handle(jmsg, ts);
				} else if (msg instanceof WelcomeMessage) {
					WelcomeMessage wmsg = (WelcomeMessage) msg;
					handle(wmsg);
				} else if (msg instanceof MulticastMessageLeave) {
					MulticastMessageLeave lmsg = (MulticastMessageLeave) msg;
					handle(lmsg, ts);
				} else if (msg instanceof GoodbyeMessage) {
					GoodbyeMessage gmsg = (GoodbyeMessage) msg;
					handle(gmsg);
				}
			}
			mmc = incoming.get();
		}
		/*
		 * Before we terminate we notify callers who are blocked in out get()
		 * method that no more gets will be added to the buffer pendingGets.
		 * This allows them to return with a null in case no message are in that
		 * buffer.
		 */
		noMoreGetsWillBeAdded = true;
		synchronized (pendingGets) {
			pendingGets.notifyAll();
		}
		log("stopping receiving thread.");
	}

	/**
	 * Will send a copy of the message to all peers who at some point sent us a
	 * Welcome message and who did not later send us a goodbye message, unless
	 * we are leaving the peer group.
	 */
	private void sendToAll(MulticastMessage msg) {
		if (isLeaving != true) {
			synchronized (timestamp) {
				MulticastMessageContainer mmc = new MulticastMessageContainer(
						msg, timestamp.getNextTimestamp());

				/* Put message in not Acknowledged map and send to self*/
				if(msg instanceof MulticastMessageLeave){
					notAcknowledgedMessages.put(LEAVE_MSG_FAKE_TIMESTAMP, Collections.list(outgoing.keys()));
				}
				else {
					notAcknowledgedMessages.put(mmc.getTimestamp(), Collections.list(outgoing.keys()));
				}
				incoming.put(mmc);

				/* Then send to the others. */
				for (PointToPointQueueSenderEnd<MulticastMessageContainer> out : outgoing
						.values()) {
					out.put(mmc);
				}
				log(mmc);
			}

		}
	}

	private void sendToAllExceptMe(MulticastMessage msg) {
		if (isLeaving != true) {
			synchronized (timestamp) {
				MulticastMessageContainer mmc = new MulticastMessageContainer(
						msg, timestamp.getNextTimestamp());
				for (PointToPointQueueSenderEnd<MulticastMessageContainer> out : outgoing
						.values()) {
					out.put(mmc);
					}
				log(mmc);
			}
		}
	}

	private void sendToknownPeer(MulticastMessage msg,
			InetSocketAddress receiverAddress) {
		synchronized (timestamp) {
			PointToPointQueueSenderEnd<MulticastMessageContainer> out = connectToPeerAt(receiverAddress);
			MulticastMessageContainer mmc = new MulticastMessageContainer(msg,
					timestamp.getNextTimestamp());
			out.put(mmc);
			log(mmc);
		}
	}

	private void sendAcknowlegdement(InetSocketAddress receiverAddress,int orgTs) {
		synchronized (timestamp) {
			PointToPointQueueSenderEnd<MulticastMessageContainer> out = outgoing
					.get(receiverAddress);
			if (out != null) {
				AcknowledgementMessage amsg = new AcknowledgementMessage(
						myAddress, orgTs);
				MulticastMessageContainer mmc = new MulticastMessageContainer(
						amsg, timestamp.getNextTimestamp());
				out.put(mmc);
				log(mmc);
			}
		}
	}

	/**
	 * The acknowledgement message
	 */

	private void handle(AcknowledgementMessage amsg, int ts) {
		synchronized (notAcknowledgedMessages) {
			List<InetSocketAddress> s = notAcknowledgedMessages
					.get(amsg.originalTimestamp);
			if (s != null) {
				s.remove(amsg.getSender());
				
				if (s.isEmpty()) {
					log("Message with ts: " + amsg.getOriginalTimestamp() + " approved!");
					log("notAcknowledgedMessages: " + notAcknowledgedMessages);
					synchronized (pendingGets) {
						pendingGets.notify();
					}
				}
			} else {
				log("Ack Msg for unknown msg: " + amsg);
			}

		}
	}

	/**
	 * A join request message is handled by connecting to the peer who wants to
	 * join and then broadcasting a join message to all peers in the current
	 * group, so that they cannot connect to the new peer too.
	 */
	private void handle(JoinRequestMessage jrmsg, int ts) {
		// When the joining peer sent the join request it connected to
		// us, so let us remember that she has a connection to us.
		synchronized (hasConnectionToUs) {
			hasConnectionToUs.add(jrmsg.getSender());
		}
		
		synchronized (timestamp) {
		// Tell the rest of the group that we have a new member.
		sendToAllExceptMe(new JoinRelayMessage(myAddress, jrmsg.getSender()));
		
		//Then we buffer a join message so it can be gotten.
		addAndNotify(pendingGets, new MulticastMessageJoin(jrmsg.getSender()),
				timestamp.getTimestamp());
		}
		// Then we connect to the new peer.
		connectToPeerAt(jrmsg.getSender());
	}

	/**
	 * A join message is handled by making a connection to the new peer plus
	 * sending her a welcome message with our own address.
	 */
	private void handle(JoinRelayMessage jmsg, int ts) {
		assert (!(jmsg.getSender().equals(myAddress))) : "Got a join message sent by myself!";
		assert (!(jmsg.getAddressOfJoiner().equals(myAddress))) : "Got a join message about my own joining!";

		// Buffer a join message so it can be gotten.
		addAndNotify(pendingGets,
				new MulticastMessageJoin(jmsg.getAddressOfJoiner()), ts);

		// Connect to the new peer and bid him welcome.
		sendToknownPeer(new WelcomeMessage(myAddress),
				jmsg.getAddressOfJoiner());
		// When this peer receives the wellcome message it will
		// connect to us, so let us remember that she has a connection
		// to us.

		synchronized (hasConnectionToUs) {
			hasConnectionToUs.add(jmsg.getAddressOfJoiner());
		}
	}

	/**
	 * A wellcome message is handled by making a connection to the existing peer
	 * who sent the wellcome message. After this, SendToAll will send a copy
	 * also to the peer who sent us this wellcome message.
	 */
	private void handle(WelcomeMessage wmsg) {
		// When the sender sent us the wellcome message it connect to
		// us, so let us remember that she has a connection to us.
		synchronized (hasConnectionToUs) {
			hasConnectionToUs.add(wmsg.getSender());
		}
		connectToPeerAt(wmsg.getSender());
	}

	/**
	 * A payload message is handled by adding it to the queue of received
	 * messages, so that it can be gotten.
	 */
	private void handle(MulticastMessagePayload<E> pmsg, int ts) {
		addAndNotify(pendingGets, pmsg, ts);
	}

	/**
	 * A leave message is handled by removing the connection to the leaving
	 * peer.
	 */
	private void handle(MulticastMessageLeave lmsg, int ts) {
		addAndNotify(pendingGets, lmsg, ts);
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
	 * A goodbye message is produced as response to a leave message and is
	 * handled by closing the connection to the existing peer who sent the
	 * goodbye message. After this, SendToAll will not send a copy to the peer
	 * who sent us this goodbye message.
	 */
	private void handle(GoodbyeMessage gmsg) {
		// Acknowledgment of the our Leave message
		synchronized (notAcknowledgedMessages) {
			List<InetSocketAddress> s = notAcknowledgedMessages
					.get(LEAVE_MSG_FAKE_TIMESTAMP);
			if (s != null) {
				s.remove(gmsg.getSender());
				if (s.isEmpty()) {
					log("Message with ts: " + LEAVE_MSG_FAKE_TIMESTAMP + " approved!");
					log("notAcknowledgedMessages: " + notAcknowledgedMessages);
					synchronized (pendingGets) {
						pendingGets.notify();
					}
				}
			}
		}
		
		// When the peer sent us the goodbye message, it closed its
		// connection to us, so let us remember that.
		synchronized (hasConnectionToUs) {
			hasConnectionToUs.remove(gmsg.getSender());
			log("now " + hasConnectionToUs.size() + " has connections to us!");
			// If we are leaving and that was the last goodbye
			// message, then we can shut down the incoming queue and
			// terminate the receiving thread.
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
	 * Used by callers to wait for objects to enter pendingGets. When the method
	 * returns, then either the collection is non-empty, or the multicast queue
	 * has seen its own leave message arrive on the incoming stream.
	 */
	private void waitForPendingGetsOrReceivedAll() {
		synchronized (pendingGets) {
			while (getNotReady() && !noMoreGetsWillBeAdded) {
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

	private boolean getNotReady() {
		synchronized (pendingGets) {
			if (pendingGets.isEmpty()){
				return true;
			}
			MulticastMessageContainer mmc = pendingGets.peek();
			List<InetSocketAddress> l; 
			//If its a LeaveMsg
			if (mmc.getMessage() instanceof MulticastMessageLeave){
				// If its our leaveMsg
				if (mmc.getMessage().getSender().equals(myAddress)){
					 l = notAcknowledgedMessages.get(LEAVE_MSG_FAKE_TIMESTAMP);
				} 
				else {
					l = notAcknowledgedMessages.get(mmc.getTimestamp());
				}
			} else {
				l = notAcknowledgedMessages.get(mmc.getTimestamp());
			}
			if(l != null){
				if(l.isEmpty()){
					return false;
				}
				else {
					return true;
				}
			}
			else {
				return false;
			}
		}
	}

	/**
	 * Used to add an element to a collection and wake up one thread waiting for
	 * elements on the collection.
	 * 
	 */

	private void addAndNotify(Collection<MulticastMessageContainer> coll,
			MulticastMessage object, int ts) {
		synchronized (coll) {
			MulticastMessageContainer mmc = new MulticastMessageContainer(
					object, ts);
			coll.add(mmc);
			// Notify that there is a new message.
			coll.notify();
		}
	}

	private <T> void addAndNotify(Collection<T> coll, T object) {
		synchronized (coll) {
			coll.add(object);
			// Notify that there is a new message.
			coll.notify();
		}
	}

	/**
	 * Used to create an outgoing queue towards the given address, including the
	 * addition of that queue to the set of queues.
	 * 
	 * @param address
	 *            The address of the peer we want to connect to. Returns null
	 *            when attempting to make connection to self.
	 */
	private PointToPointQueueSenderEnd<MulticastMessageContainer> connectToPeerAt(
			InetSocketAddress address) {
		assert (!address.equals(myAddress)) : "Cannot connect to self.";
		// Do we have a connection already?
		PointToPointQueueSenderEnd<MulticastMessageContainer> out = outgoing
				.get(address);
		assert (out == null) : "Cannot connect twice to same peer!";
		out = new PointToPointQueueSenderEndNonRobust<MulticastMessageContainer>();
		out.setReceiver(address);
		outgoing.put(address, out);
		log(myAddress + ": connects to " + address);
		return out;
	}

	/***
	 ** The part which receives puts, buffers then and sends them.
	 **/
	public void put(E object) {
		synchronized (pendingSends) {
			assert (isLeaving == false) : "Cannot put objects after calling leaveGroup()";
			addAndNotify(pendingSends, object);
		}
	}

	public void leaveGroup() {
		synchronized (pendingSends) {
			assert (isLeaving != true) : "Already left the group!";
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
	 * Used by the sending thread to wait for objects to enter the collection or
	 * us having left the group. When the method returns, then either the
	 * collection is non-empty, or the multicast queue was called in
	 * leaveGroup();
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
	 * Will take objects from pendingSends and send them to all peers. If the
	 * queue empties and leaveGroup() was called, then the queue will remain
	 * empty, so we can terminate.
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

	/**
	 * Timestamp getters
	 */
	private class Timestamp {
		private int timestamp = 0;

		public int getTimestamp() {
			return timestamp;
		}

		public int getNextTimestamp() {
			timestamp = timestamp + 1;
			return timestamp;
		}

		public void maxOfTimestamps(MulticastMessageContainer mmc) {
			int tmpTimestamp = mmc.getTimestamp();

			// Only if the message is not from our self.
			if (!mmc.getMessage().getSender().equals(myAddress)) {
				if (this.timestamp < tmpTimestamp)
					this.timestamp = tmpTimestamp;
			}
		}
	}

	private void disconnectFrom(InetSocketAddress address) {
		synchronized (outgoing) {
			PointToPointQueueSenderEnd<MulticastMessageContainer> out = outgoing
					.get(address);
			if (out != null) {
				outgoing.remove(address);
				synchronized (timestamp) {
					out.put(new MulticastMessageContainer(new GoodbyeMessage(myAddress), timestamp.getNextTimestamp()));
				}
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
		if (log)
			System.out.println(myAddress + " said: " + msg);
	}

	protected void log(MulticastMessageContainer mmc) {
		if (log) {
			if (mmc.getMessage().getSender() == myAddress)
				System.out.println(myAddress + "  sending: " + mmc);
			else
				System.out.println(myAddress + " received: " + mmc);
		}
	}
}

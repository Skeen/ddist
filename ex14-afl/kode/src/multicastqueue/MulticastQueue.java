package multicastqueue;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.SortedSet;

/**
 *
 * The sending and receiving end of a distributed queue of objects of
 * class E.  It is part of a peer group of other MulticastQueue
 * objects. An object sent by a member of the peer group should be
 * seen by all members of the peer group.  The objects must be
 * Serializable, to allow to use Java's serialization to move the
 * objects.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */

public interface MulticastQueue<E extends Serializable> extends Runnable {

	/**
	 * Specifies the message delivery guarantee of the queue. 
	 *
	 * NONE: All messages which are put into the queue will
         *       eventually arrive at all peers, including the putter.
         *       An object is delivered using a payload message (@see
         *       MulticastMessagePayload). Also, all peers are
         *       notified of new peers by receving a join message
         *       (@see MulticastMessageJoin). And, all peers are
         *       notified of leaving peers by receving a leave message
         *       (@see MulticastMessageLeave).  There are no
         *       guarantees on the order of delivery.
	 *
	 * FIFO: As NONE, plus, messages put by the same peer are
         *       delivered (usiong get) in the order they were sent by
         *       that peer. And, all messages put by a peer arrive
         *       afters its join message and before its leave message.
         *       There is no guarantee on the order of delivery of
         *       messages put by different peers.
	 *
	 * CAUSAL: As FIFO, plus, all messages are delivered in a
         *       causal order at all peers.
	 *
	 * TOTAL: AS FIFO, plus, all messages are delivered in the
         *       same order at all peers, even messages put by
         *       different peers.
	 */
	public enum DeliveryGuarantee { NONE, FIFO, CAUSAL, TOTAL };
	
	/**
	 *
	 * Used by the first group member to create a multicast queue.
	 * 
	 * @param port The port number on which this founding peer is
	 *        waiting for peers.
	 * @param deliveryGuarantee The deliveryGuarantee of the
	 *        queue.
	 * @throws IOException in case there are problems with getting
	 *         the specified port.
	 */
	public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) 
	    throws IOException;
	
	/**
	 *
	 * Used to join a peer group. This takes place by contacting
	 * one of the existing peers of the peer group. After this
	 * method invocation, the current instance of MulticastQueue
	 * is considered part of the peer group. i.e., it should
	 * receive all messages sent by other peer after it joined,
	 * and all other peers should now receive messages sent by
	 * this peer.
	 * 
	 * @param port The port number on which this peer is waiting
         *        for peers who want to join the peer group.
	 * @param deliveryGuarantee The deliveryGuarantee of the
         *        queue. Should match that of the queue that we
         *        connect to. If it does not match, then nothing is
         *        guaranteed.
	 * @param serverAddress The IP address and port of the known
	 *        peer.
	 */
	public int joinGroup(int port, 
			      InetSocketAddress knownPeer, 
			      DeliveryGuarantee deliveryGuarantee) 
	    throws IOException;

	/**
	 *
	 * Puts a message in the queue. The call should be
	 * asynchronous, i.e., it returns immediately. In particular,
	 * it might return before the object is delivered to any
	 * peer. The manager of the queue should take care of
	 * eventually moving the object to all peers in the peer
	 * group. It is a mistake to call put(E) after calling
	 * leaveGroup();
	 * 
	 * @param object The message to be added to the queue.
	 */
	public void put(E object);

	/**
	 *
	 * Will return the next message in the incoming queue. If no
	 * message is ready for delivery, then the method blocks until
	 * incoming messages arrive. Removes the message from the
	 * queue. It returns null if this queue is dead, i.e., this
	 * peer has officially left the peer group and all incoming
	 * messages have been delivered via get(). A message can
	 * either be a join message, payload message or a leave
	 * message.
	 * 
	 * @return The front of the queue, null if the queue is dead.
	 */
	public MulticastMessage get();

	/**
	 *
	 * Makes this instance of MulticastQueue leave the peer
	 * group. The other peers should be informed of this by
	 * receiving a MulticastMessageLeave. This instance itself
	 * should receive its own MulticastMessageLeave message too at
	 * some point (using get()), at which point it has
	 * "officially" left the peer group. This instance of
	 * MulticastQueue should keep receiving messages until it has
	 * officially left the group and should, if needed, keep
	 * participating in implementing the distributed queue until
	 * it has officially left the group.
	 */
	public void leaveGroup();

		
 	/**
	 *
 	 * Starts the thread manager which pushes objects to the other
 	 * peers and receives object from the other peers.
 	 */
	public void run();
	
}

package multicastqueue.total;

import multicastqueue.*;
import pointtopointqueue.PointToPointQueueReceiverEnd;
import pointtopointqueue.PointToPointQueueReceiverEndNonRobust;
import pointtopointqueue.PointToPointQueueSenderEnd;
import pointtopointqueue.PointToPointQueueSenderEndNonRobust;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 *
 * An implementation of multicastQueue, which implements the fifo
 * delivery guarantee.
 *
 * @author Original:Jesper Buus Nielsen, Aarhus University, 2012.
 *
 * Changed to also implement the total delivery guarantee
 * by Rasmus,Emil,Sverre 20083549. 2012-dDist_Q3
 */
public class MulticastQueueTotal<E extends Serializable>
    extends Thread implements MulticastQueue<E>
{
    /**
     * The address on which we listen for incoming messages.
     *
     * final but not initialized in constructor but in void init()
     */
    InetSocketAddress myAddress;

    /**
     * Used to signal that the queue is leaving the peer group. 
     */
    boolean isLeaving = false;

    /**
     * Used to signal that no more elements will be added to the queue
     * of pending gets.
     */
    boolean noMoreGetsWillBeAdded = false;

    /**
     * The peers who have a connection to us. Used to make sure that
     * we do not close down the receiving end of the queue before all
     * sending to the queue is done. Not strictly needed, but nicer.
     */
    private final HashSet<InetSocketAddress> hasConnectionToUs;

    /**
     * The incoming message queue. All other peers send their messages
     * to this queue.
     */
    final PointToPointQueueReceiverEnd<MulticastMessage> incoming;

    /**
     * Keeping track of the outgoing message queues, stored under the
     * corresponding internet address.
     */
    final ConcurrentHashMap<InetSocketAddress,PointToPointQueueSenderEnd<MulticastMessage>> outgoing;

    /**
     * Objects pending delivering locally.
     */
    final ConcurrentLinkedQueue<MulticastMessage> pendingGets;

    /**
     * Objects pending sending.
     */
    final ConcurrentLinkedQueue<E> pendingSends;

    /**
     * Sorted map of messages.
     *
     * Sorted by timestamps.
     */
    private final ConcurrentSkipListSet<TimestampedMessage> sortedMessagesPendingAcknowledge;

    /**
     * Set to keep watch over the acknowledgements every message from
     * sortedMessagesPendingAcknowledge currently has.
     *
     * Weird bug appears when making the actual MulticastMessages the
     * keys in this set.
     */
    private final Map<MulticastMessage,Set<InetSocketAddress>> currentAcknowledgements;

    /**
     * The Delivery guarantee
     *
     * final but not initialized in constructor but in void init()
     */
    DeliveryGuarantee deliveryGuarantee;

    /**
     * Holds a list of timestamps representing current knowledge
     * of every process in the network
     *
     * final but not initialized in constructor but in void init()
     */
    Clock clock;

    /**
     * For testing purposes:
     * Enables or disables total ordering on JoinMessages
     *
     * Currently disables total ordering on JoinMessages.
     * TODO: remove this by total ordering the JoinMessages.
     */
    private final static boolean orderJoinMessages = false;

    // Todo: change to require a ClockAbstractFactory
    public MulticastQueueTotal()
    {
        super("MulticastQueue"); // name of the thread

        incoming                 = new PointToPointQueueReceiverEndNonRobust<MulticastMessage>();
        pendingGets              = new ConcurrentLinkedQueue<MulticastMessage>();
        pendingSends             = new ConcurrentLinkedQueue<E>();
        outgoing                 = new ConcurrentHashMap<InetSocketAddress,PointToPointQueueSenderEnd<MulticastMessage>>();
        hasConnectionToUs        = new HashSet<InetSocketAddress>();

        currentAcknowledgements  = new HashMap<MulticastMessage,Set<InetSocketAddress>>();
        sortedMessagesPendingAcknowledge = new ConcurrentSkipListSet<TimestampedMessage>();
    }

    public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) 
        throws IOException 
    {
        // Setup initial variables: myAddress,
        // deliveryGuarantee and Clock
        init(port,deliveryGuarantee);

        log("I am creating the group");

        // Buffer a message that we have joined the group.
        MulticastMessageJoin join = new MulticastMessageJoin(myAddress);
        if (deliveryGuarantee == DeliveryGuarantee.TOTAL && orderJoinMessages)
        {
            incoming.put(new TimestampedMessage(myAddress,join, clock.clone()));
        }
        else
        {
            addAndNotify(pendingGets, join);
        }

        // Start the receiving thread.
        this.start();
        return myAddress.getPort();
    }

    public int joinGroup(int port, InetSocketAddress knownPeer,
                         DeliveryGuarantee deliveryGuarantee)
        throws IOException
    {
        // Setup initial variables: myAddress,
        // deliveryGuarantee and Clock
        init(port,deliveryGuarantee);

        log("I am joining a group");

        // Make an outgoing connection to the known peer.
        PointToPointQueueSenderEnd<MulticastMessage> out
            = connectToPeerAt(knownPeer);	
        // Send the known peer our address. 
        MulticastMessage joinRequestMessage
            = new JoinRequestMessage(myAddress);

        out.put(joinRequestMessage);
        // When the known peer receives the join request it will
        // connect to us, so let us remember that she has a connection
        // to us.
        hasConnectionToUs.add(knownPeer);

        // Buffer a message that we have joined the group.
        MulticastMessageJoin join = new MulticastMessageJoin(myAddress);
        if (deliveryGuarantee == DeliveryGuarantee.TOTAL && orderJoinMessages)
        {
            incoming.put(new TimestampedMessage(myAddress,join,clock.clone()));
            // Not needed to send acknowledgement, because the knownPeer
            // will translate the JoinRequest into an AcknowledgeMessage
            // and send it on to every peer in the network (together
            // with the knownPeer's own acknowledgeMessage).
        }
        else
        {
            addAndNotify(pendingGets, join);
        }

        // Start the receiving thread
        this.start();
        return myAddress.getPort();
    }

    /**
     * Helper method for createGroup and joinGroup.
     *
     * Takes care of choosing a port number, getting myAddress, and
     * initialized Clock uniquely to this address.
     *
     * @param port The preferred port which may or may not actually be bound
     * @param deliveryGuarantee The deliveryGuarantee to use.
     * @throws java.io.IOException If failure trying to bind the port
     * or getting the localhost.
     */
    private void init(int port, DeliveryGuarantee deliveryGuarantee)
            throws IOException
    {
        this.deliveryGuarantee = deliveryGuarantee;

        // Try to listen on the given port. Exceptions are propagated
        // out of the method.
        port = incoming.listenOnPort(port);

        // Record our address.
        InetAddress localhost = InetAddress.getLocalHost();
        String localHostAddress = localhost.getCanonicalHostName();
        myAddress = new InetSocketAddress(localHostAddress, port);

        // Initialize the Clock uniquely to this peer
//        this.clock = new VectorClock(myAddress.hashCode());
        this.clock = new LamportClock();
    }

    public MulticastMessage get()
    {		
        // Now an object is ready in pendingObjects, unless we are
        // shutting down. 
        synchronized (pendingGets) 
        {
            waitForPendingGetsOrReceivedAll();
            if (pendingGets.isEmpty()) 
            {
                return null;
                // By contract we signal shutdown by returning null.
            }
            else 
            {
                return pendingGets.poll();
            }
        }
    }

    /**
     * Starts the thread manager which pushes objects to the other
     * peers and receives object from the other peers.
     *
     * This starts a SendingThread in the background.
     * And then lets a ReceiverThread take over.
     */
    public void run()
    {
        /*
         * The thread which handles outgoing traffic.
         */
        new SendingThread<E>(this).start();

        /*
         * This is the receiving thread. This is just a dispatcher: it
         * will receive the incoming message and call the respective
         * handlers.
         */
        new ReceivingThread<E>(this).start();
    }

    /**
     * Will send a copy of the message to all peers who at some point
     * sent us a welcome message and who did not later send us a
     * goodbye message, unless we are leaving the peer group.
     * @param msg The message.
     */
    void sendToAll(MulticastMessage msg)
    {
        if (!isLeaving)
        {
            /* Send to self. */
            incoming.put(msg);
            /* Then send to the others. */
            sendToAllExceptMe(msg);
        }
    }

    private void sendToAllExceptMe(MulticastMessage msg)
    {
        if (!isLeaving)
        {
            for (PointToPointQueueSenderEnd<MulticastMessage> out :
                    outgoing.values())
            {
                out.put(msg);
            }
        }
    }

    /**
     * Used by callers to wait for objects to enter pendingGets. When
     * the method returns, then either the collection is non-empty, or
     * the multicast queue has seen its own leave message arrive on
     * the incoming stream.
     */
    private void waitForPendingGetsOrReceivedAll()
    {
        synchronized (pendingGets)
        {
            while (pendingGets.isEmpty() && !noMoreGetsWillBeAdded)
            {
                try
                {
                    // We will be woken up if an object arrives or the
                    // we received all.
                    pendingGets.wait();
                }
                catch (InterruptedException e)
                {
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
     * @param coll The collection.
     * @param object The element.
     */
    private static <T> void addAndNotify(Collection<T> coll, T object)
    {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (coll)
        {
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
     * @return A queue bound towards the address.
     */
    private PointToPointQueueSenderEnd<MulticastMessage>
    connectToPeerAt(InetSocketAddress address)
    {
        assert (!address.equals(myAddress)) : "Cannot connect to self.";
        // Do we have a connection already?
        PointToPointQueueSenderEnd<MulticastMessage> out
                = outgoing.get(address);
        assert (out == null) : "Cannot connect twice to same peer!";
        out = new PointToPointQueueSenderEndNonRobust<MulticastMessage>();
        out.setReceiver(address);
        outgoing.put(address, out);
        log("I connected to " + address);
        return out;
    }

    /***
     ** The part which receives puts, buffers then and sends them.
     **/
    public void put(E object)
    {
        synchronized(pendingSends)
        {
            assert (!isLeaving)
                    : "Cannot put objects after calling leaveGroup()";
            addAndNotify(pendingSends, object);
        }
    }

    public void leaveGroup()
    {
        synchronized (pendingSends)
        {
            assert (!isLeaving): "Already left the group!";
            MulticastMessage msg = new MulticastMessageLeave(myAddress);

            if (deliveryGuarantee == DeliveryGuarantee.TOTAL)
            {
                // LeaveMessages are not ordered...
                //msg = new TimestampedMessage(msg, clock.clone());
            }

            sendToAll(msg);
            isLeaving = true;
            // We wake up the sending thread. If pendingSends happen
            // to be empty now, the sending thread will know that we
            // are shutting down, so it will not starting waiting on
            // pendingSends again.
            pendingSends.notify();
        }
    }

    void disconnectFrom(InetSocketAddress address)
    {
        synchronized (outgoing)
        {
            PointToPointQueueSenderEnd<MulticastMessage> out
                    = outgoing.get(address);
            if (out != null)
            {
                outgoing.remove(address);
                out.put(new GoodbyeMessage(myAddress));
                log("disconnected from " + address);
                out.shutdown();
            }
        }
    }

    /**
     * Dispatch the handling of the messages.
     *
     * This dispatches the handling of the messages to a handler
     * dependent on the type of the message.
     *
     * @param msg The message to handle.
     */
    void dispatchHandling(MulticastMessage msg)
    {
        if (deliveryGuarantee == DeliveryGuarantee.TOTAL
                && msg instanceof AcknowledgeMessage) {
            handle((AcknowledgeMessage) msg);
        } else if (deliveryGuarantee == DeliveryGuarantee.TOTAL
                && msg instanceof TimestampedMessage) {
            handle((TimestampedMessage) msg);
        } else if (msg instanceof MulticastMessagePayload) {
            handle((MulticastMessagePayload<E>) msg);
        } else if (msg instanceof MulticastMessageLeave) {
            handle((MulticastMessageLeave) msg);
        } else if (msg instanceof JoinRequestMessage) {
            handle((JoinRequestMessage)msg);
        } else if (msg instanceof JoinRelayMessage) {
            handle((JoinRelayMessage)msg);
        } else if (msg instanceof WelcomeMessage) {
            handle((WelcomeMessage)msg);
        } else if (msg instanceof GoodbyeMessage) {
            handle((GoodbyeMessage)msg);
        } else
            throw new UnsupportedOperationException(
                    "Can't handle this type of message: "+ msg);
    }

    /**
     * A join request message is handled by connecting to the peer who
     * wants to join and then broadcasting a join message to all peers
     * in the current group, so that they cannot connect to the new
     * peer too.
     * @param msg The message.
     */
    void handle(JoinRequestMessage msg)
    {
        log(msg);

        // Buffer a join message so it can be gotten.
        MulticastMessageJoin join = new MulticastMessageJoin(msg.getSender());

        if (deliveryGuarantee == DeliveryGuarantee.TOTAL && orderJoinMessages )
        {
            // Add to sorted queue and await the moment where this peer is
            // the only peer not acknowledging, and then acknowledge.
            // This is to make sure all others have connected to
            // the newPeer before newPeer is fully joined.

            // This message should be timestamped with the senders timestamp.
            // However that would always be a new joiner anyway, so can assume a
            // newly constructed Clock.
            sortedMessagesPendingAcknowledge.add(
                    new TimestampedMessage(join, new LamportClock()));
        }
        else
        {
            addAndNotify(pendingGets,join);
        }

        // When the joining peer sent the join request it connected to
        // us, so let us remember that she has a connection to us.
        synchronized(hasConnectionToUs)
        {
            hasConnectionToUs.add(msg.getSender());
        }

        // Then we tell the rest of the group that we have a new member.
        JoinRelayMessage joinRelayMessage = new JoinRelayMessage(myAddress, msg.getSender());
        if (deliveryGuarantee == DeliveryGuarantee.TOTAL && orderJoinMessages)
        {
            // This message should be timestamped with the senders timestamp.
            // However that would always be a new joiner anyway, so can assume a
            // newly constructed Clock.
            Clock newClock = new LamportClock();
            sendToAllExceptMe(joinRelayMessage);

            // And fake the acknowledgement from the newPeer (to the others)
            sendToAllExceptMe(new AcknowledgeMessage(join, newClock));
        }
        else
        {
            sendToAllExceptMe(joinRelayMessage);
        }
        // Then we connect to the new peer.
        connectToPeerAt(msg.getSender());
    }

    /**
     * A join message is handled by making a connection to the new
     * peer plus sending her a welcome message with our own address.
     * @param msg The message.
     */
    void handle(JoinRelayMessage msg)
    {
        log(msg);
        assert (!(msg.getSender().equals(myAddress)))
            : "Got a join message sent by myself!";
        assert (!(msg.getAddressOfJoiner().equals(myAddress)))
            : "Got a join message about my own joining!";

        // Buffer a join message so it can be gotten.
        MulticastMessageJoin join = new MulticastMessageJoin(msg.getAddressOfJoiner());
        if (deliveryGuarantee == DeliveryGuarantee.TOTAL && orderJoinMessages)
        {
            // This message should be timestamped with the senders timestamp.
            // However that would always be a new joiner anyway, so can assume a
            // newly constructed Clock.
            incoming.put(new TimestampedMessage(join, new LamportClock()));
        }
        else
        {
            addAndNotify(pendingGets, join);
        }

        // Connect to the new peer
        PointToPointQueueSenderEnd<MulticastMessage> out
                = connectToPeerAt(msg.getAddressOfJoiner());
        // Bid the new peer welcome.
        out.put(new WelcomeMessage(myAddress));
        // When this peer receives the welcome message it will
        // connect to us, so let us remember that she has a connection
        // to us.

        synchronized(hasConnectionToUs)
        {
            hasConnectionToUs.add(msg.getAddressOfJoiner());
        }
    }

    /**
     * A welcome message is handled by making a connection to the
     * existing peer who sent the welcome message. After this,
     * SendToAll will send a copy also to the peer who sent us this
     * welcome message.
     * @param msg The message.
     */
    void handle(WelcomeMessage msg)
    {
        log(msg);
        // When the sender sent us the welcome message it connect to
        // us, so let us remember that she has a connection to us.
        synchronized(hasConnectionToUs)
        {
            hasConnectionToUs.add(msg.getSender());
        }
        connectToPeerAt(msg.getSender());
    }

    /**
     * A payload message is handled by adding it to the queue of
     * received messages, so that it can be gotten.
     * @param msg The message.
     */
    void handle(MulticastMessagePayload<E> msg)
    {
        log(msg);
        addAndNotify(pendingGets, msg);
    }

    /**
     * A leave message is handled by removing the connection to the
     * leaving peer.
     * @param msg The message.
     */
    void handle(MulticastMessageLeave msg)
    {
        log(msg);
        addAndNotify(pendingGets, msg);
        InetSocketAddress address = msg.getSender();
        if (!address.equals(myAddress))
        {
            disconnectFrom(address);
        }
        else
        {
            // That was my own leave message. If I'm the only one left
            // in the group, then this means that I can safely shut
            // down.
            if (hasConnectionToUs.isEmpty())
            {
                incoming.shutdown();
            }
        }
    }

    /**
     * A goodbye message is produced as response to a leave message
     * and is handled by closing the connection to the existing peer
     * who sent the goodbye message. After this, SendToAll will not
     * send a copy to the peer who sent us this goodbye message.
     * @param msg The message.
     */
    void handle(GoodbyeMessage msg)
    {
        log(msg);

        // When the peer sent us the goodbye message, it closed its
        // connection to us, so let us remember that.
        synchronized(hasConnectionToUs)
        {
            hasConnectionToUs.remove(msg.getSender());
            log("now " + hasConnectionToUs.size() + " has connections to us!");
            // If we are leaving and that was the last goodbye
            // message, then we can shut down the incoming queue and
            // terminate the receiving thread.
            if (hasConnectionToUs.isEmpty() && isLeaving)
            {
                // If the receiving thread is blocked on the incoming
                // queue, it will be woken up and receive a null when
                // the queue is empty, which will tell it that we have
                // received all messages.
                incoming.shutdown();
            }
        }
    }

    /**
     * An AcknowledgeMessage is produced as a means to know which peer
     * has received which messages and is a part of the total ordering
     * scheme.
     *
     * Responsibilities:
     *  Sort messages after timestamps
     *  Maintain set of Acknowledgments for each message
     *  If the tip of the sorted messages has Acknowledgments from all peers in
     *  'hasConnectionToUs', pipes the messages in the tip to pendingGets.
     *
     * @param acknowledgeMessage The message
     */
    void handle(AcknowledgeMessage acknowledgeMessage)
    {
        assert deliveryGuarantee == DeliveryGuarantee.TOTAL;

        log(acknowledgeMessage);

        // Update the localClock with the new information.
        clock.updateAccordingTo(acknowledgeMessage.getClock());
        clock.incrementLocalTimestamp();

        Set<InetSocketAddress> acknowledgements;
        MulticastMessage message = acknowledgeMessage.getMessage();

        // Update pendingAcknowledge with the new acknowledgement
        if (currentAcknowledgements.containsKey(message))
        {
            acknowledgements = currentAcknowledgements.get(message);
        }
        else
        {
            acknowledgements = new HashSet<InetSocketAddress>();
            currentAcknowledgements.put(message,acknowledgements);
        }
        acknowledgements.add(acknowledgeMessage.getSender());

        // Empty from the tip, if currentAcknowledgements report a full set
        // of acknowledgements.
        while (!sortedMessagesPendingAcknowledge.isEmpty())
        {
            // Get first message from the tip of the queue.
            message = sortedMessagesPendingAcknowledge.first().getMessage();

            // I should have knowledge of acknowledgements for this message;
            if (!currentAcknowledgements.containsKey(message))
                break;

            // Test on who has acknowledged this message.
            acknowledgements = currentAcknowledgements.get(message);

            // Is it a full set of acknowledgements?
            if (!acknowledgements.containsAll(hasConnectionToUs)) {
                // No more of the tip can be popped, as it requires a full set of acknowledges.
                break;
            }

            // Is it a full set of acknowledgements? - including myAddress?
            if (!acknowledgements.contains(myAddress))
            {
                // Special case to handle delaying of the acknowledge of a JoinMessage until
                // only the knownPeer, who received the JoinRequest, lacks acknowledging.
                if (message instanceof MulticastMessageJoin)
                {
                    incoming.put(new TimestampedMessage(myAddress, message, clock.clone()));
                }

                // No more of the tip can be popped, as it requires a full set of acknowledges.
                break;
            }

            // Passed all tests: The tip is fully acknowledged.

            // Poll messages of the tip ...

            TimestampedMessage out = sortedMessagesPendingAcknowledge.pollFirst();
            currentAcknowledgements.remove(message);

            // ...and to the application
            addAndNotify(pendingGets, message);

            log("Yei! - Fully acknowledged a message from earlier "
                    + out.getClock() + " @ "+clock+"\n\t" + message);
        }
    }

    /**
     * If deliveryGuarantee is total-ordering, then extract the Clock,
     * handle acknowledges, send messages to sortedMessagesPendingAcknowledge
     * for delay until acknowledged.
     *
     * @param timestampedMessage The timestamped message.
     */
    void handle(TimestampedMessage timestampedMessage)
    {
        assert deliveryGuarantee == DeliveryGuarantee.TOTAL;
        log(timestampedMessage);

        // For testing purposes
        String tmp = clock.toString();

        // Update the local clock with the new information.
        clock.updateAccordingTo(timestampedMessage.getClock());
        clock.incrementLocalTimestamp();

        // For testing purposes - continued
        if (!tmp.equals(clock.toString()))
        {
            String test1 = "I updated my clock from " + tmp + " to ";
            log(test1 + clock + " in regards to " + timestampedMessage.getClock());
        }

        MulticastMessage message = timestampedMessage.getMessage();

        if (message instanceof MulticastMessagePayload)
        {
            // Add to sorted queue ordered after timestamp.
            sortedMessagesPendingAcknowledge.add(timestampedMessage);

            // Send acknowledgement that the message was received to all.
            sendToAll(new AcknowledgeMessage(myAddress, message, clock.clone()));
        }
        else if (message instanceof MulticastMessageJoin)
        {
            if (orderJoinMessages)
                throw new IllegalStateException("Should order JOins");

            if (hasConnectionToUs.contains(message.getSender()) )// && !message.getSender().equals(myAddress))
            {
                // The JoinMessage should not be handled, as already joined to peer.
                log("Already Connected to this peer: " + timestampedMessage);
                sendToAllExceptMe(new AcknowledgeMessage(myAddress,message,clock.clone()));
                return;
            }

            // This should be handled the same way as Payload messages.
            // Except, there is some bug with too many JoinMessages.

            // Add to sorted queue ordered after timestamp, if not already there
            // todo: Cleanup JoinMessages, so no longer needing extra sorting.
            List<MulticastMessage> alreadySorted = new ArrayList<MulticastMessage>();
            // HeadSet because only necessary to look in the timestamps earlier than timestampedMessage
            for (TimestampedMessage each : sortedMessagesPendingAcknowledge.headSet(timestampedMessage))
            {
                alreadySorted.add(each.getMessage());
            }

            // Make sure the sortedMessages contains the joinMessage
            if (! alreadySorted.contains(timestampedMessage.getMessage()))
            {
                log("Sorting this message " + timestampedMessage);
                sortedMessagesPendingAcknowledge.add(timestampedMessage);
            }
            else
            {
                log("Not sorting this: " + timestampedMessage);
            }

            // Send acknowledgement that the message was received to all.
            sendToAll(new AcknowledgeMessage(myAddress, message, clock.clone()));
        }
        else
        {
            throw new IllegalStateException("An unexpected msg of "
                    +message.getClass()+" wanted handling!");
        }
    }

    /***
     ** HELPERS FOR DEBUGGING
     **/
    protected boolean log = false;

    public void printLog()
    {
        log = true;
    }

    void log(String msg) {
        if (log)
        {
            System.out.println(myAddress + " said: " + msg);
            System.out.println();
        }
    }

    void log(MulticastMessage msg)
    {
        if (log)
        {
            System.out.println(myAddress + " received:\n\t " + msg);
            System.out.println();
        }
    }
}


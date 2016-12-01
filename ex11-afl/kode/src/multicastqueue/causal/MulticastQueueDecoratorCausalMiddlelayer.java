package multicastqueue.causal;

import multicastqueue.MulticastMessage;
import multicastqueue.MulticastMessagePayload;
import multicastqueue.MulticastQueue;
import multicastqueue.total.Timestamp;
import multicastqueue.total.TimestampedMessage;
import multicastqueue.total.VectorClock;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings({"ALL"})
public class MulticastQueueDecoratorCausalMiddlelayer<E extends Serializable> implements MulticastQueue<E>
{
    private final MulticastQueue<TimestampedMessage> q;
    private VectorClock vc;
    private int processID = -1;

    /**
     * Creates a middlelayer between the Application layer (MultiChat) and
     * the NetWorkLayer (multicastqueue) allowing total ordering of the messages.
     *
     * @param q The queue to wrap with a middlelayer
     */
    public MulticastQueueDecoratorCausalMiddlelayer(MulticastQueue<TimestampedMessage> q) {
        this.q = q;
    }

    /**
     * Creates a middlelayer between the Application layer (MultiChat) and
     * the NetWorkLayer (multicastqueue) allowing total ordering of the messages.
     *
     * This allows the user to specify the unique processID of the program in stead
     * of the program requesting the processID on the network.
     *
     * @param q The queue to wrap with a middlelayer
     * @param processID The unique identifier for this peer/process on the network.
     */
    public MulticastQueueDecoratorCausalMiddlelayer(MulticastQueue<TimestampedMessage> q, int processID) {
        this(q);
        this.processID = processID;
    }

    @Override
    public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) throws IOException {

        // I am the first process in the group.
        vc = new VectorClock(0);
        return q.createGroup(port,deliveryGuarantee);

    }

    @Override
    public int joinGroup(int port, InetSocketAddress knownPeer, DeliveryGuarantee deliveryGuarantee)
            throws IOException
    {
        // request the processID if not earlier known.
        if (processID < 0)
            processID = requestAvailableProcessID(knownPeer);

        vc = new VectorClock(processID);

        return q.joinGroup(port,knownPeer,deliveryGuarantee);
    }


    /**
     * Finds a unique ID that is a key to the VectorClock for what the
     * local process knows about the work done by the process' by
     * knownPeer.
     *
     * This started out being just a positive int being the index in
     * an array in VectorClock, but it might suit better with a
     * InetSocketAddress as key.
     *
     * @param knownPeer The owner of the process' to ID.
     * @return A unique ID of the process' run by knownPeer.
     */
    private int requestAvailableProcessID(InetSocketAddress knownPeer) {
        // Todo: remove fake it code.
        return 1;
    }

    @Override
    public void put (E object) {
        vc.incrementLocalTimestamp();

        //q.put( TimestampedMessage.augmentedCarCdr(object, vc.getTimestamp()));
        q.put( new TimestampedMessage((MulticastMessage) object,vc));
    }

    @Override
    public MulticastMessage get()
    {
        MulticastMessage res = q.get();

        if (res instanceof MulticastMessagePayload)
        {
            //payload unload
            TimestampedMessage message = (TimestampedMessage) ((MulticastMessagePayload) res).getPayload();

            //Integer[] timestamp = (Integer[]) TimestampedMessage.cdr(res);
            VectorClock vc = (VectorClock) message.getClock();
            res = (MulticastMessage) message.getMessage();
            //res = (MulticastMessage) TimestampedMessage.car(res);

            while (shouldDelayThisTimestamp(vc.getLocalTimestamp()))
                delayIt();

            this.vc.updateAccordingTo(vc);
            this.vc.incrementLocalTimestamp();
        }

        return res;
    }

    private void delayIt() {
        throw new UnsupportedOperationException("Cannot Delay now");
    }

    /**
     * The delivery of the message with the timestamp should be delayed until
     * two conditions are met:
     *
     * cond1: ts[i] = VC[i]+1
     * cond2: ts_m[k] <= VC[k] for all k != i
     *
     *
     * @param timestamp The timestamp of the message.
     * @return whether the timestamp comes from a message that should be delayed.
     */
    private boolean shouldDelayThisTimestamp(Integer[] timestamp) {
        boolean cond1,cond2 = true;

        // The local timestamp should be 1 more than the msg knows
        cond1 = timestamp[processID] == (vc.getTimestamp(processID).getValue() + 1);

        for (int k = 0; k < timestamp.length; k++)
        {
            // skip this process' timestamp as already checked.
            if (k == processID)
                continue;

            // All other timestamps must be earlier or current to
            // my knowledge, or I should wait.
            cond2 &= (timestamp[k] <= vc.getTimestamp(k).getValue());
        }

        // should delay if not both conditions are met
        return !(cond1 && cond2);
    }

    private boolean shouldDelayThisTimestamp(Timestamp timestamp) {
        return vc.getLocalTimestamp().compareTo(timestamp) < 0;
    }

    @Override
    public void leaveGroup() {
        q.leaveGroup();
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("This should only be called internally");
    }
}
